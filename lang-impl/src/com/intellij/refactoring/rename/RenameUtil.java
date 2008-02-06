package com.intellij.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

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
    RenamePsiElementProcessor theProcessor = null;

    Collection<PsiReference> refs = null;
    for(RenamePsiElementProcessor processor: Extensions.getExtensions(RenamePsiElementProcessor.EP_NAME)) {
      if (processor.canProcessElement(element)) {
        theProcessor = processor;
        refs = processor.findReferences(element);
        break;
      }
    }
    if (refs == null) {
      refs = ReferencesSearch.search(element).findAll();
    }
    for (PsiReference ref : refs) {
      PsiElement referenceElement = ref.getElement();
      result.add(new MoveRenameUsageInfo(referenceElement, ref, ref.getRangeInElement().getStartOffset(),
                                         ref.getRangeInElement().getEndOffset(), element,
                                         false));
    }

    for(RenameCollisionDetector collisionDetector: Extensions.getExtensions(RenameCollisionDetector.EP_NAME)) {
      collisionDetector.findCollisions(element, newName, allRenames, result);
    }


    if (searchInStringsAndComments && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, false
                                                                                    ? NonCodeSearchDescriptionLocation.NON_JAVA
                                                                                    : NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, false, theProcessor);
        TextOccurrencesUtil.UsageInfoFactory factory = new NonCodeUsageInfoFactory(element, stringToReplace);
        TextOccurrencesUtil.addUsagesInStringsAndComments(element, stringToSearch, result, factory);
      }
    }


    if (searchForTextOccurences && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, true
                                                                                    ? NonCodeSearchDescriptionLocation.NON_JAVA
                                                                                    : NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);

      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, true, theProcessor);
        addTextOccurence(element, result, projectScope, stringToSearch, stringToReplace);

        if (theProcessor != null) {
          Pair<String, String> additionalStringToSearch = theProcessor.getTextOccurrenceSearchStrings(element, newName);
          if (additionalStringToSearch != null) {
            addTextOccurence(element, result, projectScope, additionalStringToSearch.first, additionalStringToSearch.second);
          }
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
    try {
      for(RenamePsiElementProcessor processor: Extensions.getExtensions(RenamePsiElementProcessor.EP_NAME)) {
        if (processor.canProcessElement(element)) {
          processor.renameElement(element, newName, usages, listener);
          return;
        }
      }

      doRenameGenericNamedElement(element, newName, usages, listener);
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
            // TODO[yole]: convert HelpID.getRenameHelpID() to ElementDescriptionProvider
            CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), e.getMessage(), null, project);
          }
        });
    }
  }

  public static void doRenameGenericNamedElement(PsiElement namedElement, String newName, UsageInfo[] usages, RefactoringElementListener listener)
    throws IncorrectOperationException {
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
}
