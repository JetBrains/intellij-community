package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.xml.XmlFoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;

import java.util.*;

class FoldingPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingPolicy");

  private FoldingPolicy() {}

  /**
   * Returns map from element to range to fold, elements are sorted in reverse start offset order
   */
  public static TreeMap<PsiElement, TextRange> getElementsToFold(PsiElement file, Document document) {
    TreeMap<PsiElement, TextRange> map = new TreeMap<PsiElement, TextRange>(new Comparator<PsiElement>() {
      public int compare(PsiElement element, PsiElement element1) {
        int startOffsetDiff = element1.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
        return startOffsetDiff != 0 ? startOffsetDiff :
               element1.getTextRange().getEndOffset() - element.getTextRange().getEndOffset();
      }
    });
    final Language lang = file.getLanguage();
    final FoldingBuilder foldingBuilder = lang.getFoldingBuilder();
    if (foldingBuilder != null) {
      final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(file.getNode(), document);
      for (FoldingDescriptor descriptor : foldingDescriptors) {
        map.put(SourceTreeToPsiMap.treeElementToPsi(descriptor.getElement()), descriptor.getRange());
      }
      return map;
    }

    if (file instanceof PsiJavaFile) {
      PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        PsiImportStatementBase[] statements = importList.getAllImportStatements();
        if (statements.length > 1) {
          final TextRange rangeToFold = getRangeToFold(importList);
          if (rangeToFold != null) {
            map.put(importList, rangeToFold);
          }
        }
      }

      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      for (PsiClass aClass : classes) {
        ProgressManager.getInstance().checkCanceled();
        addElementsToFold(map, aClass, document, true);
      }

      TextRange range = getFileHeader((PsiJavaFile)file);
      if (range != null && document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
        map.put(file, range);
      }
    }

    return map;
  }

  private static void addAnnotationsToFold(PsiModifierList modifierList, final Map<PsiElement, TextRange> foldElements, Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        int j;
        addToFold(foldElements, child, document, false);
        for (j = i + 1; j < children.length; j++) {
          PsiElement nextChild = children[j];
          if (nextChild instanceof PsiModifier) break;
        }
        i = j;
      }
    }
  }

  private static void findClasses(PsiElement scope, final Map<PsiElement, TextRange> foldElements, final Document document) {
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitClass(PsiClass aClass) {
        addToFold(foldElements, aClass, document, true);
        addElementsToFold(foldElements, aClass, document, false);
      }
    });
  }

  private static void addElementsToFold(Map<PsiElement, TextRange> map, PsiClass aClass, Document document, boolean foldJavaDocs) {
    if (!(aClass.getParent() instanceof PsiJavaFile) || ((PsiJavaFile)aClass.getParent()).getClasses().length > 1) {
      addToFold(map, aClass, document, true);
    }

    PsiDocComment docComment;
    if (foldJavaDocs) {
      docComment = aClass.getDocComment();
      if (docComment != null) {
        addToFold(map, docComment, document, true);
      }
    }
    addAnnotationsToFold(aClass.getModifierList(), map, document);

    PsiElement[] children = aClass.getChildren();
    for (PsiElement child : children) {
      ProgressManager.getInstance().checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addToFold(map, method, document, true);
        addAnnotationsToFold(method.getModifierList(), map, document);

        if (foldJavaDocs) {
          docComment = method.getDocComment();
          if (docComment != null) {
            addToFold(map, docComment, document, true);
          }
        }

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          findClasses(body, map, document);
        }
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;
        if (foldJavaDocs) {
          docComment = field.getDocComment();
          if (docComment != null) {
            addToFold(map, docComment, document, true);
          }
        }
        addAnnotationsToFold(field.getModifierList(), map, document);
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          findClasses(initializer, map, document);
        } else if (field instanceof PsiEnumConstant) {
          findClasses(field, map, document);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        addToFold(map, initializer, document, true);
        findClasses(initializer, map, document);
      }
      else if (child instanceof PsiClass) {
        addElementsToFold(map, (PsiClass)child, document, true);
      }
    }
  }

  public static TextRange getRangeToFold(PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiCodeBlock body = ((PsiMethod)element).getBody();
      if (body == null) return null;
      return body.getTextRange();
    }
    else if (element instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)element).getBody().getTextRange();
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiJavaToken lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiJavaToken rBrace = aClass.getRBrace();
      if (rBrace == null) return null;
      return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
    }
    else if (element instanceof PsiJavaFile) {
      return getFileHeader((PsiJavaFile)element);
    }
    else if (element instanceof PsiImportList) {
      PsiImportList list = (PsiImportList)element;
      PsiImportStatementBase[] statements = list.getAllImportStatements();
      if (statements.length == 0) return null;
      final PsiElement importKeyword = statements[0].getFirstChild();
      if (importKeyword == null) return null;
      int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
      int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    else if (element instanceof PsiDocComment) {
      return element.getTextRange();
    }
    else if (element instanceof XmlTag) {
      final FoldingBuilder foldingBuilder = element.getLanguage().getFoldingBuilder();

      if (foldingBuilder instanceof XmlFoldingBuilder) {
        return ((XmlFoldingBuilder)foldingBuilder).getRangeToFold(element);
      }
    } else if (element instanceof PsiAnnotation) {
      int startOffset = element.getTextRange().getStartOffset();
      PsiElement last = element;
      while(element instanceof PsiAnnotation) {
        last = element;
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
      }

      return new TextRange(startOffset, last.getTextRange().getEndOffset());
    }


    return null;
  }

  private static TextRange getFileHeader(PsiJavaFile file) {
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    PsiElement element = first;
    while (element instanceof PsiComment) {
      element = element.getNextSibling();
      if (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      else {
        break;
      }
    }
    if (element == null) return null;
    if (element.getPrevSibling() instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null || element.equals(first)) return null;
    return new TextRange(first.getTextOffset(), element.getTextOffset());
  }

  public static String getFoldingText(PsiElement element) {
    final Language lang = element.getLanguage();
    final FoldingBuilder foldingBuilder = lang.getFoldingBuilder();
    if (foldingBuilder != null) {
      return foldingBuilder.getPlaceholderText(element.getNode());
    }

    if (element instanceof PsiImportList) {
      return "...";
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiClass) {
      return "{...}";
    }
    else if (element instanceof PsiDocComment) {
      return "/**...*/";
    }
    else if (element instanceof PsiFile) {
      return "/.../";
    }
    else if (element instanceof PsiAnnotation) {
      return "@{...}";
    }
    else {
      LOG.error("Unknown element:" + element);
      return null;
    }
  }


  public static boolean isCollapseByDefault(PsiElement element) {
    final Language lang = element.getLanguage();
    final FoldingBuilder foldingBuilder = lang.getFoldingBuilder();
    if (foldingBuilder != null) {
      return foldingBuilder.isCollapsedByDefault(element.getNode());
    }

    CodeFoldingSettings settings = CodeFoldingSettings.getInstance();
    if (element instanceof PsiImportList) {
      return settings.isCollapseImports();
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer) {
      if (element instanceof PsiMethod && isSimplePropertyAccessor((PsiMethod)element)) {
        return settings.isCollapseAccessors();
      }
      return settings.isCollapseMethods();
    }
    else if (element instanceof PsiAnonymousClass) {
      return settings.isCollapseAnonymousClasses();
    }
    else if (element instanceof PsiClass) {
      return !(element.getParent() instanceof PsiFile) && settings.isCollapseInnerClasses();
    }
    else if (element instanceof PsiDocComment) {
      return settings.isCollapseJavadocs();
    }
    else if (element instanceof PsiJavaFile) {
      return settings.isCollapseFileHeader();
    }
    else if (element instanceof PsiAnnotation) {
      return settings.isCollapseAnnotations();
    }
    else {
      LOG.error("Unknown element:" + element);
      return false;
    }
  }

  private static boolean isSimplePropertyAccessor(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) return false;
    PsiStatement statement = statements[0];
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          return ((PsiReferenceExpression)returnValue).resolve() instanceof PsiField;
        }
      }
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      if (statement instanceof PsiExpressionStatement) {
        PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
        if (expr instanceof PsiAssignmentExpression) {
          PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
          PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
          if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression)lhs).resolve() instanceof PsiField &&
                   ((PsiReferenceExpression)rhs).resolve() instanceof PsiParameter;
          }
        }
      }
    }
    return false;
  }

  private static <T extends PsiNamedElement> int getChildIndex(T element, PsiElement parent, String name, Class<T> hisClass) {
    PsiElement[] children = parent.getChildren();
    int index = 0;

    for (PsiElement child : children) {
      if (hisClass.isAssignableFrom(child.getClass())) {
        T namedChild = (T)child;
        final String childName = namedChild.getName();

        if ((name != null && name.equals(childName)) || name == childName) {
          if (namedChild.equals(element)) {
            return index;
          }
          index++;
        }
      }
    }

    return index;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getSignature(PsiElement element) {
    if (element instanceof PsiImportList) {
      PsiFile file = element.getContainingFile();
      if (file instanceof PsiJavaFile && element.equals(((PsiJavaFile)file).getImportList())) {
        return "imports";
      }
      else {
        return null;
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiElement parent = method.getParent();

      StringBuffer buffer = new StringBuffer();
      buffer.append("method#");
      String name = method.getName();
      buffer.append(name);
      buffer.append("#");
      buffer.append(getChildIndex(method, parent, name, PsiMethod.class));

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiElement parent = aClass.getParent();

      StringBuffer buffer = new StringBuffer();
      buffer.append("class#");
      if (parent instanceof PsiClass || parent instanceof PsiFile) {
        String name = aClass.getName();
        buffer.append(name);
        buffer.append("#");
        buffer.append(getChildIndex(aClass, parent, name, PsiClass.class));

        if (parent instanceof PsiClass) {
          String parentSignature = getSignature(parent);
          if (parentSignature == null) return null;
          buffer.append(";");
          buffer.append(parentSignature);
        }
      }
      else {
        buffer.append(aClass.getTextRange().getStartOffset());
        buffer.append(":");
        buffer.append(aClass.getTextRange().getEndOffset());
      }

      return buffer.toString();
    }
    else if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer)element;
      PsiElement parent = initializer.getParent();

      StringBuffer buffer = new StringBuffer();
      buffer.append("initializer#");

      int index = 0;
      PsiElement[] children = parent.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiClassInitializer) {
          if (child.equals(initializer)) break;
          index++;
        }
      }
      buffer.append("#");
      buffer.append(index);

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    else if (element instanceof PsiField) { // needed for doc-comments only
      PsiField field = (PsiField)element;
      PsiElement parent = field.getParent();

      StringBuffer buffer = new StringBuffer();
      buffer.append("field#");
      String name = field.getName();
      buffer.append(name);

      buffer.append("#");
      buffer.append(getChildIndex(field, parent, name, PsiField.class));

      if (parent instanceof PsiClass) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) return null;
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    else if (element instanceof PsiDocComment) {
      StringBuffer buffer = new StringBuffer();
      buffer.append("docComment;");

      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass) && !(parent instanceof PsiMethod) && !(parent instanceof PsiField)) {
        return null;
      }
      String parentSignature = getSignature(parent);
      if (parentSignature == null) return null;
      buffer.append(parentSignature);

      return buffer.toString();
    }
    else if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      PsiElement parent = tag.getParent();

      StringBuffer buffer = new StringBuffer();
      buffer.append("tag#");
      String name = tag.getName();
      buffer.append(name.length() == 0 ? "<unnamed>" : name);

      buffer.append("#");
      buffer.append(getChildIndex(tag, parent, name, XmlTag.class));

      if (parent instanceof XmlTag) {
        String parentSignature = getSignature(parent);
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    else if (element instanceof PsiJavaFile) {
      return null;
    }
    return null;
  }

  private static <T extends PsiNamedElement> T restoreElementInternal(PsiElement parent, String name, int index, Class<T> hisClass) {
    PsiElement[] children = parent.getChildren();

    for (PsiElement child : children) {
      if (hisClass.isAssignableFrom(child.getClass())) {
        T namedChild = (T)child;
        final String childName = namedChild.getName();

        if ((name != null && name.equals(childName)) || name == childName) {
          if (index == 0) {
            return namedChild;
          }
          index--;
        }
      }
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static PsiElement restoreBySignature(PsiFile file, String signature) {
    int semicolonIndex = signature.indexOf(";");
    PsiElement parent;

    if (semicolonIndex >= 0) {
      String parentSignature = signature.substring(semicolonIndex + 1);
      parent = restoreBySignature(file, parentSignature);
      if (parent == null) return null;
      signature = signature.substring(0, semicolonIndex);
    }
    else {
      parent = file;
    }

    StringTokenizer tokenizer = new StringTokenizer(signature, "#");
    String type = tokenizer.nextToken();

    if (type.equals("imports")) {
      if (!(file instanceof PsiJavaFile)) return null;
      return ((PsiJavaFile)file).getImportList();
    }
    else if (type.equals("method")) {
      String name = tokenizer.nextToken();
      try {
        int index = Integer.parseInt(tokenizer.nextToken());
        return restoreElementInternal(parent, name, index, PsiMethod.class);
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    else if (type.equals("class")) {
      String name = tokenizer.nextToken();

      PsiNameHelper nameHelper = file.getManager().getNameHelper();
      if (nameHelper.isIdentifier(name)) {
        int index = 0;
        try {
          index = Integer.parseInt(tokenizer.nextToken());
        }
        catch (NoSuchElementException e) { //To read previous XML versions correctly
        }

        return restoreElementInternal(parent, name, index, PsiClass.class);
      }
      else {
        StringTokenizer tok1 = new StringTokenizer(name, ":");
        int start = Integer.parseInt(tok1.nextToken());
        int end = Integer.parseInt(tok1.nextToken());
        PsiElement element = file.findElementAt(start);
        if (element != null) {
          TextRange range = element.getTextRange();
          while (range != null && range.getEndOffset() < end) {
            element = element.getParent();
            range = element.getTextRange();
          }

          if (range != null && range.getEndOffset() == end && element instanceof PsiClass) {
            return element;
          }
        }
      }

      return null;
    }
    else if (type.equals("initializer")) {
      try {
        int index = Integer.parseInt(tokenizer.nextToken());

        PsiElement[] children = parent.getChildren();
        for (PsiElement child : children) {
          if (child instanceof PsiClassInitializer) {
            if (index == 0) {
              return child;
            }
            index--;
          }
        }

        return null;
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    else if (type.equals("field")) {
      String name = tokenizer.nextToken();

      try {
        int index = 0;
        try {
          index = Integer.parseInt(tokenizer.nextToken());
        }
        catch (NoSuchElementException e) { //To read previous XML versions correctly
        }

        return restoreElementInternal(parent, name, index, PsiField.class);
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }

    }
    else if (type.equals("docComment")) {
      if (parent instanceof PsiClass) {
        return ((PsiClass)parent).getDocComment();
      }
      else if (parent instanceof PsiMethod) {
        return ((PsiMethod)parent).getDocComment();
      }
      else if (parent instanceof PsiField) {
        return ((PsiField)parent).getDocComment();
      }
      else {
        return null;
      }
    }
    else if (type.equals("tag")) {
      String name = tokenizer.nextToken();

      if (parent instanceof XmlFile) {
        parent = ((XmlFile)parent).getDocument();
      }

      try {
        int index = Integer.parseInt(tokenizer.nextToken());
        PsiElement result = restoreElementInternal(parent, name, index, XmlTag.class);

        if (result == null &&
            file.getFileType() == StdFileTypes.JSP) {
          //TODO: FoldingBuilder API, psi roots, etc?
          if (parent instanceof XmlDocument) {
            // html tag, not found in jsp tree
            result = restoreElementInternal(HtmlUtil.getRealXmlDocument((XmlDocument)parent), name, index, XmlTag.class);
          }
          else if (name.equals("<unnamed>")) {
            // scriplet/declaration missed because null name
            result = restoreElementInternal(parent, "", index, XmlTag.class);
          }
        }

        return result;
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    else {
      return null;
    }
  }

  private static boolean addToFold(Map<PsiElement, TextRange> map, PsiElement elementToFold, Document document, boolean allowOneLiners) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return false;

    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());
    if (!allowOneLiners) {
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine < endLine) {
        map.put(elementToFold, range);
        return true;
      }
      else {
        return false;
      }
    }
    else {
      if (range.getEndOffset() - range.getStartOffset() > getFoldingText(elementToFold).length()) {
        map.put(elementToFold, range);
        return true;
      }
      else {
        return false;
      }
    }
  }
}
