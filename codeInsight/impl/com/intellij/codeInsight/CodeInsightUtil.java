package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiProximityComparator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 *
 */
public class CodeInsightUtil {
  public static PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
    if (!file.getViewProvider().getRelevantLanguages().contains(StdLanguages.JAVA)) return null;
    final PsiExpression expression = findElementInRange(file, startOffset, endOffset, PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) return null;
    return expression;
  }

  public static <T extends PsiElement> T findElementInRange(PsiFile file, int startOffset, int endOffset, Class<T> klass) {
    return CodeInsightUtilBase.findElementInRange(file, startOffset, endOffset, klass, StdLanguages.JAVA);
  }

  @NotNull
  public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {
    if (!file.getViewProvider().getRelevantLanguages().contains(StdLanguages.JAVA)) return PsiElement.EMPTY_ARRAY;
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, StdLanguages.JAVA);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, StdLanguages.JAVA);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof PsiStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent instanceof PsiCodeBlock) break;
      if (PsiUtil.isInJspFile(parent) && parent instanceof PsiFile) break;
      if (parent instanceof PsiCodeFragment) break;
      if (parent == null || parent instanceof PsiFile) return PsiElement.EMPTY_ARRAY;
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset()) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset()) return PsiElement.EMPTY_ARRAY;

    if (parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement &&
        element1 == ((PsiCodeBlock)parent).getLBrace() && element2 == ((PsiCodeBlock)parent).getRBrace()) {
      return new PsiElement[]{parent.getParent()};
    }

/*
    if(parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement) {
      return new PsiElement[]{parent.getParent()};
    }
*/

    PsiElement[] children = parent.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PsiStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        return PsiElement.EMPTY_ARRAY;
      }
    }

    return array.toArray(new PsiElement[array.size()]);
  }

  public static void sortIdenticalShortNameClasses(PsiClass[] classes, @NotNull PsiElement context) {
    if (classes.length <= 1) return;

    Arrays.sort(classes, new PsiProximityComparator(context, context.getProject()));
  }

  public static PsiExpression[] findExpressionOccurrences(PsiElement scope, PsiExpression expr) {
    List<PsiExpression> array = new ArrayList<PsiExpression>();
    addExpressionOccurrences(RefactoringUtil.unparenthesizeExpression(expr), array, scope);
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addExpressionOccurrences(PsiExpression expr, List<PsiExpression> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        if (areExpressionsEquivalent(RefactoringUtil.unparenthesizeExpression((PsiExpression)child), expr)) {
          array.add((PsiExpression)child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child);
    }
  }

  public static PsiExpression[] findReferenceExpressions(PsiElement scope, PsiElement referee) {
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    addReferenceExpressions(array, scope, referee);
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addReferenceExpressions(ArrayList<PsiElement> array, PsiElement scope, PsiElement referee) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiReferenceExpression) {
        PsiElement ref = ((PsiReferenceExpression)child).resolve();
        if (ref != null && PsiEquivalenceUtil.areElementsEquivalent(ref, referee)) {
          array.add(child);
        }
      }
      addReferenceExpressions(array, child, referee);
    }
  }

  public static boolean areExpressionsEquivalent(PsiExpression expr1, PsiExpression expr2) {
    return PsiEquivalenceUtil.areElementsEquivalent(expr1, expr2);
  }

  public static Editor positionCursor(final Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean preparePsiElementForWrite(PsiElement element) {
    PsiFile file = element == null ? null : element.getContainingFile();
    return prepareFileForWrite(file);
  }
  public static boolean preparePsiElementsForWrite(Collection<? extends PsiElement> elements) {
    if (elements.isEmpty()) return true;
    Set<VirtualFile> files = new THashSet<VirtualFile>();
    Project project = null;
    for (PsiElement element : elements) {
      project = element.getProject();
      PsiFile file = element.getContainingFile();
      if (file == null) continue;
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) continue;
      files.add(virtualFile);
    }
    if (!files.isEmpty()) {
      VirtualFile[] virtualFiles = files.toArray(new VirtualFile[files.size()]);
      ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(virtualFiles);
      return !status.hasReadonlyFiles();
    }
    return true;
  }

  public static boolean prepareFileForWrite(final PsiFile file) {
    if (file == null) return false;

    if (!file.isWritable()) {
      final Project project = file.getProject();

      final Editor editor =
        FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile()), true);

      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (editor != null && editor.getComponent().isDisplayable()) {
              HintManager.getInstance()
                .showErrorHint(editor, CodeInsightBundle.message("error.hint.file.is.readonly", file.getVirtualFile().getPresentableUrl()));
            }
          }
        });

        return false;
      }
    }

    return true;
  }

  private static final Key<Boolean> ANT_FILE_SIGN = new Key<Boolean>("FORCED ANT FILE");

  public static boolean isAntFile(final PsiFile file) {
    if (file instanceof XmlFile) {
      final XmlFile xmlFile = (XmlFile)file;
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && "project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
          if (tag.getAttributeValue("default") != null) {
            return true;
          }
          VirtualFile vFile = xmlFile.getVirtualFile();
          if (vFile == null) {
            final PsiFile origFile = xmlFile.getOriginalFile();
            if (origFile != null) {
              vFile = origFile.getVirtualFile();
            }
          }
          if (vFile != null && vFile.getUserData(ANT_FILE_SIGN) != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static Set<PsiType> addSubtypes(PsiType psiType, 
                                         final PsiElement context,
                                         final boolean getRawSubtypes) {
    int arrayDim = psiType.getArrayDimensions();

    psiType = psiType.getDeepComponentType();
    if (psiType instanceof PsiClassType) {
      Set<PsiType> result = new HashSet<PsiType>();
      getSubtypes(context, (PsiClassType)psiType, arrayDim, getRawSubtypes, result);
      return result;
    }

    return Collections.emptySet();
  }

  private static void getSubtypes(final PsiElement context, final PsiClassType baseType, final int arrayDim,
                                  final boolean getRawSubtypes, final Set<PsiType> result){
    final PsiClassType.ClassResolveResult baseResult = baseType.resolveGenerics();
    final PsiClass baseClass = baseResult.getElement();
    final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();
    if(baseClass == null) return;

    final Query<PsiClass> query = new FilteredQuery<PsiClass>(
      ClassInheritorsSearch.search(baseClass, context.getResolveScope(), true, false, false), new Condition<PsiClass>() {
      public boolean value(final PsiClass psiClass) {
        return !(psiClass instanceof PsiTypeParameter);
      }
    });

    final PsiManager manager = context.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();

    query.forEach(new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        if(!facade.getResolveHelper().isAccessible(inheritor, context, null)) return true;

        if(inheritor.getUserData(CompletionUtil.COPY_KEY) != null){
          final PsiClass newClass = (PsiClass) inheritor.getUserData(CompletionUtil.COPY_KEY);
          if(newClass.isValid())
            inheritor = newClass;
        }

        if(inheritor.getQualifiedName() == null && !manager.areElementsEquivalent(inheritor.getContainingFile(), context.getContainingFile())){
          return true;
        }

        if (CompletionUtil.isInExcludedPackage(inheritor)) return true;

        PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
        if(superSubstitutor == null) return true;
        if(getRawSubtypes){
          result.add(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
          return true;
        }

        PsiSubstitutor inheritorSubstitutor = PsiSubstitutor.EMPTY;
        final Iterator<PsiTypeParameter> inheritorParamIter = PsiUtil.typeParametersIterator(inheritor);
        while (inheritorParamIter.hasNext()) {
          PsiTypeParameter inheritorParameter = inheritorParamIter.next();

          final Iterator<PsiTypeParameter> baseParamIter = PsiUtil.typeParametersIterator(baseClass);
          while (baseParamIter.hasNext()) {
            PsiTypeParameter baseParameter = baseParamIter.next();
            final PsiType substituted = superSubstitutor.substitute(baseParameter);
            PsiType arg = baseSubstitutor.substitute(baseParameter);
            if (arg instanceof PsiWildcardType) arg = ((PsiWildcardType)arg).getBound();
            PsiType substitution = resolveHelper.getSubstitutionForTypeParameter(inheritorParameter,
                                                                                 substituted,
                                                                                 arg,
                                                                                 true,
                                                                                 PsiUtil.getLanguageLevel(context));
            if (substitution == PsiType.NULL || substitution instanceof PsiWildcardType) continue;
            if (substitution == null) {
              result.add(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
              return true;
            }
            inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
            break;
          }
        }

        PsiType toAdd = createType(inheritor, inheritorSubstitutor, arrayDim);
        if (baseType.isAssignableFrom(toAdd)) {
          result.add(toAdd);
        }
        return true;
      }
    });
  }

  private static PsiType createType(PsiClass cls,
                             PsiSubstitutor currentSubstitutor,
                             int arrayDim) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(cls.getProject()).getElementFactory();
    PsiType newType = elementFactory.createType(cls, currentSubstitutor);
    for(int i = 0; i < arrayDim; i++){
      newType = newType.createArrayType();
    }
    return newType;
  }
}
