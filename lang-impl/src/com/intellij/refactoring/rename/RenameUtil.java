package com.intellij.refactoring.rename;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class RenameUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameUtil");

  private RenameUtil() {
  }

  @NotNull
  public static UsageInfo[] findUsages(final PsiElement element,
                                       String newName,
                                       boolean searchInStringsAndComments,
                                       boolean searchForTextOccurences,
                                       Map<? extends PsiElement, String> allRenames) {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();

    PsiManager manager = element.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);

    Collection<PsiReference> refs = processor.findReferences(element);
    for (PsiReference ref : refs) {
      PsiElement referenceElement = ref.getElement();
      result.add(new MoveRenameUsageInfo(referenceElement, ref, ref.getRangeInElement().getStartOffset(),
                                         ref.getRangeInElement().getEndOffset(), element,
                                         false));
    }

    processor.findCollisions(element, newName, allRenames, result);

    if (searchInStringsAndComments && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, false, processor);
        TextOccurrencesUtil.UsageInfoFactory factory = new NonCodeUsageInfoFactory(element, stringToReplace);
        TextOccurrencesUtil.addUsagesInStringsAndComments(element, stringToSearch, result, factory);
      }
    }


    if (searchForTextOccurences && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.NON_JAVA);

      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, true, processor);
        addTextOccurence(element, result, projectScope, stringToSearch, stringToReplace);

        Pair<String, String> additionalStringToSearch = processor.getTextOccurrenceSearchStrings(element, newName);
        if (additionalStringToSearch != null) {
          addTextOccurence(element, result, projectScope, additionalStringToSearch.first, additionalStringToSearch.second);
        }
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private static void addTextOccurence(final PsiElement element, final List<UsageInfo> result, final GlobalSearchScope projectScope,
                                       final String stringToSearch,
                                       final String stringToReplace) {
    TextOccurrencesUtil.UsageInfoFactory factory = new TextOccurrencesUtil.UsageInfoFactory() {
      public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
        TextRange textRange = usage.getTextRange();
        int start = textRange == null ? 0 : textRange.getStartOffset();
        return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
      }
    };
    TextOccurrencesUtil.addTextOccurences(element, stringToSearch, projectScope, result, factory);
  }


  public static void buildPackagePrefixChangedMessage(final VirtualFile[] virtualFiles, StringBuffer message, final String qualifiedName) {
    if (virtualFiles.length > 0) {
      message.append(RefactoringBundle.message("package.occurs.in.package.prefixes.of.the.following.source.folders.n", qualifiedName));
      for (final VirtualFile virtualFile : virtualFiles) {
        message.append(virtualFile.getPresentableUrl()).append("\n");
      }
      message.append(RefactoringBundle.message("these.package.prefixes.will.be.changed"));
    }
  }

  private static String getStringToReplace(PsiElement element, String newName, boolean nonJava, final RenamePsiElementProcessor theProcessor) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)element;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }

    if (theProcessor != null) {
      String result = theProcessor.getQualifiedNameAfterRename(element, newName, nonJava);
      if (result != null) {
        return result;
      }
    }

    if (element instanceof PsiNamedElement) {
      return newName;
    }
    else {
      LOG.error("Unknown element type");
      return null;
    }
  }

  public static String getQualifiedNameAfterRename(String qName, String newName) {
    if (qName == null) return newName;
    int index = qName.lastIndexOf('.');
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }

  public static void checkRename(PsiElement element, String newName) throws IncorrectOperationException {
    if (element instanceof PsiCheckedRenameElement) {
      ((PsiCheckedRenameElement)element).checkSetName(newName);
    }
  }

  public static void doRename(final PsiElement element, String newName, UsageInfo[] usages, final Project project,
                              RefactoringElementListener listener) {
    final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
    try {
      processor.renameElement(element, newName, usages, listener);
    }
    catch (final IncorrectOperationException e) {
      // may happen if the file or package cannot be renamed. e.g. locked by another application
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
        //LOG.error(e);
        //return;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), e.getMessage(), processor.getHelpID(element), project);
          }
        });
    }
  }

  public static void doRenameGenericNamedElement(PsiElement namedElement, String newName, UsageInfo[] usages,
                                                 RefactoringElementListener listener) throws IncorrectOperationException {
    PsiWritableMetaData writableMetaData = null;
    if (namedElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)namedElement).getMetaData();
      if (metaData instanceof PsiWritableMetaData) {
        writableMetaData = (PsiWritableMetaData)metaData;
      }
    }
    if (writableMetaData == null && !(namedElement instanceof PsiNamedElement)) {
      LOG.error("Unknown element type");
    }

    for (UsageInfo usage : usages) {
      rename(usage, newName);
    }

    if (writableMetaData != null) {
      writableMetaData.setName(newName);
    }
    else {
      ((PsiNamedElement)namedElement).setName(newName);
    }

    listener.elementRenamed(namedElement);
  }

  public static void rename(UsageInfo info, String newName) throws IncorrectOperationException {
    if (info.getElement() == null) return;
    PsiReference ref = info.getReference();
    if (ref == null) return;
    ref.handleElementRename(newName);
  }

  public static void removeConflictUsages(Set<UsageInfo> usages) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (usageInfo instanceof UnresolvableCollisionUsageInfo) {
        iterator.remove();
      }
    }
  }

  public static Collection<String> getConflictDescriptions(UsageInfo[] usages) {
    ArrayList<String> descriptions = new ArrayList<String>();

    for (UsageInfo usage : usages) {
      if (usage instanceof UnresolvableCollisionUsageInfo) {
        descriptions.add(((UnresolvableCollisionUsageInfo)usage).getDescription());
      }
    }
    return descriptions;
  }

  public static void renameNonCodeUsages(@NotNull Project project, @NotNull NonCodeUsageInfo[] usages) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Map<Document, ArrayList<UsageOffset>> docsToOffsetsMap = new HashMap<Document, ArrayList<UsageOffset>>();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    for (NonCodeUsageInfo usage : usages) {
      PsiElement element = usage.getElement();

      if (element == null) continue;
      element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
      if (element == null) continue;
      final PsiFile containingFile = element.getContainingFile();
      final Document document = psiDocumentManager.getDocument(containingFile);
      int fileOffset = element.getTextRange().getStartOffset() + usage.startOffset;

      ArrayList<UsageOffset> list = docsToOffsetsMap.get(document);
      if (list == null) {
        list = new ArrayList<UsageOffset>();
        docsToOffsetsMap.put(document, list);
      }
      list.add(new UsageOffset(fileOffset, fileOffset + usage.endOffset - usage.startOffset, usage.newText));
    }

    for (Document document : docsToOffsetsMap.keySet()) {
      ArrayList<UsageOffset> list = docsToOffsetsMap.get(document);
      UsageOffset[] offsets = list.toArray(new UsageOffset[list.size()]);
      Arrays.sort(offsets);

      for (int i = offsets.length - 1; i >= 0; i--) {
        UsageOffset usageOffset = offsets[i];
        document.replaceString(usageOffset.startOffset, usageOffset.endOffset, usageOffset.newText);
      }
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }

  public static boolean isValidName(final Project project, final PsiElement psiElement, final String newName) {
    if (newName == null || newName.length() == 0) {
      return false;
    }
    final Condition<String> inputValidator = RenameInputValidatorRegistry.getInstance().getInputValidator(psiElement);
    if (inputValidator != null) {
      return inputValidator.value(newName);
    }
    if (psiElement instanceof PsiFile || psiElement instanceof PsiDirectory) {
      return newName.indexOf(File.separatorChar) < 0 && newName.indexOf('/') < 0;
    }

    PsiFile f = psiElement.getContainingFile();
    Language language = f == null ? psiElement.getLanguage() : f.getLanguage();

    return LanguageNamesValidation.INSTANCE.forLanguage(language).isIdentifier(newName.trim(), project);
  }

  private static class UsageOffset implements Comparable<UsageOffset> {
    final int startOffset;
    final int endOffset;
    final String newText;

    public UsageOffset(int startOffset, int endOffset, String newText) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.newText = newText;
    }

    public int compareTo(final UsageOffset o) {
      return startOffset - o.startOffset;
    }
  }
}
