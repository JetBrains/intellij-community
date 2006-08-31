package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class JavaDocUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocUtil");

  private static final @NonNls Pattern ourTypePattern = Pattern.compile("[ ]+[^ ^\\[^\\]]");
  private static final @NonNls Pattern ourLtFixupPattern = Pattern.compile("<([^/^\\w^!])");
  private static final @NonNls Pattern ourToQuote = Pattern.compile("[\\\\\\.\\^\\$\\?\\*\\+\\|\\)\\}\\]\\{\\(\\[]");
  private static final @NonNls String LT_ENTITY = "&lt;";

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void createHyperlink(StringBuffer buffer, String refText,String label,boolean plainLink) {
    buffer.append("<a href=\"");
    buffer.append("psi_element://"); // :-)
    buffer.append(refText);
    buffer.append("\">");
    if (!plainLink) {
      buffer.append("<code>");
    }
    buffer.append(label);
    if (!plainLink) {
      buffer.append("</code>");
    }
    buffer.append("</a>");
  }

  public static String[] getDocPaths(Project project) {
    ArrayList<String> result = new ArrayList<String>();

    final VirtualFile[] roots = ProjectRootManagerEx.getInstanceEx(project).getRootFiles(ProjectRootType.JAVADOC);
    for (VirtualFile root : roots) {
      if (!(root.getFileSystem() instanceof HttpFileSystem)) {
        result.add(root.getUrl());
      }
    }

    return (String[])result.toArray(new String[result.size()]);
  }

  /**
   * Extracts a reference to a source element from the beginning of the text.
   * 
   * @return length of the extracted reference
   */
  public static int extractReference(String text) {
    int lparenthIndex = text.indexOf('(');
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    if (lparenthIndex < 0) {
      return spaceIndex;
    }
    else {
      if (spaceIndex < lparenthIndex) {
        return spaceIndex;
      }
      int rparenthIndex = text.indexOf(')', lparenthIndex);
      if (rparenthIndex < 0) {
        rparenthIndex = text.length() - 1;
      }
      return rparenthIndex + 1;
    }
  }

  public static PsiElement findReferenceTarget(PsiManager manager, String refText, PsiElement context) {
    LOG.assertTrue(context == null || context.isValid());

    int poundIndex = refText.indexOf('#');
    if (poundIndex < 0) {
      PsiClass aClass = manager.getResolveHelper().resolveReferencedClass(refText, context);

      if (aClass == null) aClass = manager.findClass(refText, context.getResolveScope());

      if (aClass != null) return aClass.getNavigationElement();
      PsiPackage aPackage = manager.findPackage(refText);
      if (aPackage!=null) return aPackage;
      JavaDocManager.DocumentationProvider provider = JavaDocManager.getInstance(manager.getProject()).getProviderFromElement(context);
      if (provider!=null) {
        return provider.getDocumentationElementForLink(refText,context);
      }
      return null;
    }
    else {
      String classRef = refText.substring(0, poundIndex).trim();
      if (classRef.length() > 0) {
        PsiClass aClass = manager.getResolveHelper().resolveReferencedClass(classRef, context);

        if (aClass == null) aClass = manager.findClass(classRef, context.getResolveScope());

        if (aClass == null) return null;
        return findReferencedMember(aClass, refText.substring(poundIndex + 1), context);
      }
      else {
        String memberRefText = refText.substring(1);
        PsiElement scope = context;
        while (true) {
          if (scope instanceof PsiFile) break;
          if (scope instanceof PsiClass) {
            PsiElement member = findReferencedMember((PsiClass)scope, memberRefText, context);
            if (member != null) return member;
          }
          scope = scope.getParent();
        }
        return null;
      }
    }
  }

  private static PsiElement findReferencedMember(PsiClass aClass, String memberRefText, PsiElement context) {
    int parenthIndex = memberRefText.indexOf('(');
    if (parenthIndex < 0) {
      String name = memberRefText;
      PsiField field = aClass.findFieldByName(name, true);
      if (field != null) return field.getNavigationElement();
      PsiClass inner = aClass.findInnerClassByName(name, true);
      if (inner != null) return inner.getNavigationElement();
      PsiMethod[] methods = aClass.getAllMethods();
      for (PsiMethod method : methods) {
        if (method.getName().equals(name)) return method.getNavigationElement();
      }
      return null;
    }
    else {
      String name = memberRefText.substring(0, parenthIndex).trim();
      int rparenIndex = memberRefText.lastIndexOf(')');
      if (rparenIndex == -1) return null;
      
      String parmsText = memberRefText.substring(parenthIndex + 1, rparenIndex).trim();
      StringTokenizer tokenizer = new StringTokenizer(parmsText.replaceAll("[*]", ""), ",");
      PsiType[] types = new PsiType[tokenizer.countTokens()];
      int i = 0;
      PsiElementFactory factory = aClass.getManager().getElementFactory();
      while (tokenizer.hasMoreTokens()) {
        String parmText = tokenizer.nextToken().trim();
        try {
          Matcher typeMatcher = ourTypePattern.matcher(parmText);
          String typeText = parmText;

          if (typeMatcher.find()) {
            typeText = parmText.substring(0, typeMatcher.start());
          }

          PsiType type = factory.createTypeFromText(typeText, context);
          types[i++] = type;
        }
        catch (IncorrectOperationException e) {
          LOG.info(e);
        }
      }
      PsiMethod[] methods = aClass.getAllMethods();
      MethodsLoop:
      for (PsiMethod method : methods) {
        if (!method.getName().equals(name)) continue;
        PsiParameter[] parms = method.getParameterList().getParameters();
        if (parms.length != types.length) continue;

        for (int k = 0; k < parms.length; k++) {
          PsiParameter parm = parms[k];
          if (
            types[k] != null && !TypeConversionUtil.erasure(parm.getType()).getCanonicalText().equals(types[k].getCanonicalText())
            ) {
            continue MethodsLoop;
          }
        }

        int hashIndex = memberRefText.indexOf('#',rparenIndex);
        if (hashIndex != -1) {
          int parameterNumber = Integer.parseInt(memberRefText.substring(hashIndex + 1));
          if (parameterNumber < parms.length) return method.getParameterList().getParameters()[parameterNumber].getNavigationElement();
        }
        return method.getNavigationElement();
      }
      return null;
    }
  }

  public static String getReferenceText(Project project, PsiElement element) {
    if (element instanceof PsiPackage) {
      return ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiClass) {
      final String refText = ((PsiClass)element).getQualifiedName();
      if (refText != null) return refText;
      return ((PsiClass)element).getName();
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      String name = field.getName();
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        return getReferenceText(project, aClass) + "#" + name;
      }
      else {
        return "#" + name;
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      String name = method.getName();
      StringBuffer buffer = new StringBuffer();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        buffer.append(getReferenceText(project, aClass));
      }
      buffer.append("#");
      buffer.append(name);
      buffer.append("(");
      PsiParameter[] parms = method.getParameterList().getParameters();
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(project);
      boolean spaceBeforeComma = styleSettings.SPACE_BEFORE_COMMA;
      boolean spaceAfterComma = styleSettings.SPACE_AFTER_COMMA;
      for (int i = 0; i < parms.length; i++) {
        PsiParameter parm = parms[i];
        String typeText = parm.getType().getCanonicalText();
        buffer.append(typeText);
        if (i < parms.length - 1) {
          if (spaceBeforeComma) {
            buffer.append(" ");
          }
          buffer.append(",");
          if (spaceAfterComma) {
            buffer.append(" ");
          }
        }
      }
      buffer.append(")");
      return buffer.toString();
    }
    else if (element instanceof PsiParameter) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method != null) {
        return getReferenceText(project, method) +
               "#"+
               ((PsiParameterList)element.getParent()).getParameterIndex((PsiParameter)element);
      }
    }

    return null;
  }

  public static String getShortestClassName(PsiClass aClass, PsiElement context) {
    String qName = aClass.getQualifiedName();

    if (qName == null) return aClass.getName();

    String packageName = ((PsiJavaFile)aClass.getContainingFile()).getPackageName();

    if (packageName.length() == 0) return qName;

    String shortName = packageName.length() < qName.length() ? qName.substring(packageName.length() + 1) : qName;

    return
      aClass.equals(aClass.getManager().getResolveHelper().resolveReferencedClass(shortName, context)) ?
      shortName :
      qName;
  }

  public static String getLabelText(Project project, PsiManager manager, String refText, PsiElement context) {
    PsiElement refElement = findReferenceTarget(manager, refText, context);
    if (refElement == null) {
      return refText.replaceFirst("^#", "").replaceAll("#", ".");
    }
    int poundIndex = refText.indexOf('#');
    if (poundIndex < 0) {
      if (refElement instanceof PsiClass) {
        return getShortestClassName((PsiClass)refElement, context);
      }
      else {
        return refText;
      }
    }
    else {
      PsiClass aClass = null;
      if (refElement instanceof PsiField) {
        aClass = ((PsiField)refElement).getContainingClass();
      }
      else if (refElement instanceof PsiMethod) {
        aClass = ((PsiMethod)refElement).getContainingClass();
      }
      else if (refElement instanceof PsiClass){
        return refText.replaceAll("#", ".");
      }
      if (aClass == null) return refText;
      String classRef = refText.substring(0, poundIndex).trim();
      String memberText = refText.substring(poundIndex + 1);
      String memberLabel = getMemberLabelText(project, manager, memberText, context);
      if (classRef.length() > 0) {
        PsiElement refClass = findReferenceTarget(manager, classRef, context);
        if (refClass instanceof PsiClass) {
          PsiElement scope = context;
          while (true) {
            if (scope == null || scope instanceof PsiFile) break;
            if (scope.equals(refClass)) {
              return memberLabel;
            }
            scope = scope.getParent();
          }
        }
        return getLabelText(project, manager, classRef, context) + "." + memberLabel;
      }
      else {
        return memberLabel;
      }
    }
  }

  private static String getMemberLabelText(Project project, PsiManager manager, String memberText, PsiElement context) {
    int parenthIndex = memberText.indexOf('(');
    if (parenthIndex < 0) return memberText;
    if (!StringUtil.endsWithChar(memberText, ')')) return memberText;
    String parms = memberText.substring(parenthIndex + 1, memberText.length() - 1);
    StringBuffer buffer = new StringBuffer();
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(project);
    boolean spaceBeforeComma = styleSettings.SPACE_BEFORE_COMMA;
    boolean spaceAfterComma = styleSettings.SPACE_AFTER_COMMA;
    StringTokenizer tokenizer = new StringTokenizer(parms, ",");
    while (tokenizer.hasMoreTokens()) {
      String param = tokenizer.nextToken().trim();
      int index1 = param.indexOf('[');
      if (index1 < 0) index1 = param.length();
      int index2 = param.indexOf(' ');
      if (index2 < 0) index2 = param.length();
      int index = Math.min(index1, index2);
      String className = param.substring(0, index).trim();
      String shortClassName = getLabelText(project, manager, className, context);
      buffer.append(shortClassName);
      buffer.append(param.substring(className.length()));
      if (tokenizer.hasMoreElements()) {
        if (spaceBeforeComma) {
          buffer.append(" ");
        }
        buffer.append(",");
        if (spaceAfterComma) {
          buffer.append(" ");
        }
      }
    }
    return memberText.substring(0, parenthIndex + 1) + buffer.toString() + ")";
  }

  private static String quote(String x) {
    if (ourToQuote.matcher(x).find()) {
      return "\\" + x;
    }

    return x;
  }

  public static String fixupText(String docText) {
    Matcher fixupMatcher = ourLtFixupPattern.matcher(docText);
    LinkedList<String> secondSymbols = new LinkedList<String>();

    while (fixupMatcher.find()) {
      String s = fixupMatcher.group(1);

      //[db] that's workaround to avoid internal bug
      if (!s.equals("\\")) {
        secondSymbols.addFirst(s);
      }
    }

    for (String s : secondSymbols) {
      String pattern = "<" + quote(s);

      try {
        docText = Pattern.compile(pattern).matcher(docText).replaceAll(LT_ENTITY + pattern);
      }
      catch (PatternSyntaxException e) {
        LOG.error("Pattern syntax exception on " + pattern);
      }
    }

    return docText;
  }

  public static PsiClassType[] getImplementsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getImplementsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  public static PsiClassType[] getExtendsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getExtendsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final void formatEntityName(String type, String name, StringBuffer destination) {
    destination.append(type).append(":&nbsp;<b>").append(name).append("</b><br>");
  }
}