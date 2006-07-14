/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2001
 * Time: 4:54:17 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

/**
 * @author max
 */
public abstract class HTMLComposer {
  protected HTMLExporter myExporter;
  private int[] myListStack;
  private int myListStackTop;
  @NonNls protected static final String BR = "<br>";
  @NonNls protected static final String NBSP = "&nbsp;";
  @NonNls protected static final String CODE_CLOSING = "</code>";
  @NonNls protected static final String CODE_OPENING = "<code>";
  @NonNls protected static final String FONT_CLOSING = "</font>";
  @NonNls protected static final String B_OPENING = "<b>";
  @NonNls protected static final String B_CLOSING = "</b>";
  @NonNls protected static final String FONT_OPENING = "<font style=\"font-family:verdana;\"";
  @NonNls protected static final String A_HREF_OPENING = "<a HREF=\"";
  @NonNls protected static final String A_CLOSING = "</a>";

  protected HTMLComposer() {
    myListStack = new int[5];
    myListStackTop = -1;
  }

  public abstract void compose(StringBuffer buf, RefEntity refEntity);

  public void compose(StringBuffer buf, RefEntity refElement, CommonProblemDescriptor descriptor) {}

  public void composeWithExporter(StringBuffer buf, RefEntity refEntity, HTMLExporter exporter) {
    myExporter = exporter;
    compose(buf, refEntity);
    myExporter = null;
  }

  protected void genPageHeader(final StringBuffer buf, RefEntity refEntity) {
    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement)refEntity;

      appendHeading(buf, InspectionsBundle.message("inspection.offline.view.tool.display.name.title"));
      buf.append(BR);
      appendAfterHeaderIndention(buf);
      appendAccessModifier(buf, refElement);
      appendShortName(buf, refElement);
      buf.append(BR).append(BR);

      appendHeading(buf, InspectionsBundle.message("inspection.export.results.capitalized.location"));
      buf.append(BR);
      appendAfterHeaderIndention(buf);
      appendLocation(buf, refElement);
      buf.append(BR).append(BR);
    }
  }

  public static void appendHeading(@NonNls final StringBuffer buf, String name) {
    buf.append(
      "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<font style=\"font-family:verdana; font-weight:bold; color:#005555\"; size = \"3\">");
    buf.append(name);
    buf.append(":</font>");
  }

  private static void appendAccessModifier(@NonNls final StringBuffer buf, RefElement refElement) {
    String modifier = refElement.getAccessModifier();
    if (modifier != null && modifier != PsiModifier.PACKAGE_LOCAL) {
      buf.append(modifier);
      buf.append(NBSP);
    }
  }

  private void appendLocation(final StringBuffer buf, final RefElement refElement) {
    RefEntity owner = refElement.getOwner();
    buf.append(FONT_OPENING);
    if (owner instanceof RefPackage) {
      buf.append(InspectionsBundle.message("inspection.export.results.package"));
      buf.append(NBSP).append(CODE_OPENING);
      buf.append(RefUtil.getInstance().getPackageName(refElement));
      buf.append(CODE_CLOSING);
    }
    else if (owner instanceof RefMethod) {
      buf.append(InspectionsBundle.message("inspection.export.results.method"));
      buf.append(NBSP);
      appendElementReference(buf, (RefElement)owner);
    }
    else if (owner instanceof RefField) {
      buf.append(InspectionsBundle.message("inspection.export.results.field"));
      buf.append(NBSP);
      appendElementReference(buf, (RefElement)owner);
      buf.append(NBSP);
      buf.append(InspectionsBundle.message("inspection.export.results.initializer"));
    }
    else if (owner instanceof RefClass) {
      appendClassOrInterface(buf, (RefClass)owner, false);
      buf.append(NBSP);
      appendElementReference(buf, (RefElement)owner);
    } else if (refElement instanceof RefFile){ //todo
      buf.append(InspectionsBundle.message("inspection.export.results.file"));
      buf.append(NBSP);
      appendElementReference(buf, refElement, false);
    }
    buf.append(FONT_CLOSING);
  }

  protected static void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      buf.append(capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.interface") : InspectionsBundle.message("inspection.export.results.interface"));
    }
    else if (refClass.isAbstract()) {
      buf.append(capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.abstract.class") : InspectionsBundle.message("inspection.export.results.abstract.class"));
    }
    else {
      buf.append(capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.class") : InspectionsBundle.message("inspection.export.results.class"));
    }
  }

  protected static String getClassOrInterface(RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.interface") : InspectionsBundle.message("inspection.export.results.interface");
    }
    else if (refClass.isAbstract()) {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.abstract.class") : InspectionsBundle.message("inspection.export.results.abstract.class");
    }
    else {
      return capitalizeFirstLetter ? InspectionsBundle.message("inspection.export.results.capitalized.class") : InspectionsBundle.message("inspection.export.results.class");
    }
  }

  private static void appendShortName(final StringBuffer buf, RefElement refElement) {
    refElement.accept(new RefVisitor() {
      public void visitClass(RefClass refClass) {
        if (refClass.isStatic()) {
          buf.append(InspectionsBundle.message("inspection.export.results.static"));
          buf.append(NBSP);
        }

        appendClassOrInterface(buf, refClass, false);
        buf.append(NBSP).append(B_OPENING).append(CODE_OPENING);
        buf.append(refClass.getName());
        buf.append(CODE_CLOSING).append(B_CLOSING);
      }

      public void visitField(RefField field) {
        PsiField psiField = (PsiField)field.getElement();

        if (field.isStatic()) {
          buf.append(InspectionsBundle.message("inspection.export.results.static"));
          buf.append(NBSP);
        }

        buf.append(InspectionsBundle.message("inspection.export.results.field"));
        buf.append(NBSP).append(CODE_OPENING);

        buf.append(psiField.getType().getPresentableText());
        buf.append(NBSP).append(B_OPENING);
        buf.append(psiField.getName());
        buf.append(B_CLOSING).append(CODE_CLOSING);
      }

      public void visitMethod(RefMethod method) {
        PsiMethod psiMethod = (PsiMethod)method.getElement();
        PsiType returnType = psiMethod.getReturnType();

        if (method.isStatic()) {
          buf.append(InspectionsBundle.message("inspection.export.results.static"));
          buf.append(NBSP);
        }
        else if (method.isAbstract()) {
          buf.append(InspectionsBundle.message("inspection.export.results.abstract"));
          buf.append(NBSP);
        }
        buf.append(method.isConstructor() ? InspectionsBundle.message("inspection.export.results.constructor") : InspectionsBundle.message("inspection.export.results.method"));
        buf.append(NBSP).append(CODE_OPENING);

        if (returnType != null) {
          buf.append(returnType.getPresentableText());
          buf.append(NBSP);
        }

        buf.append(B_OPENING);
        buf.append(psiMethod.getName());
        buf.append(B_CLOSING);
        appendMethodParameters(buf, psiMethod, true);
        buf.append(CODE_CLOSING);
      }

      public void visitFile(RefFile file) {
        final PsiFile psiFile = file.getElement();
        buf.append(B_OPENING);
        buf.append(psiFile.getName());
        buf.append(B_CLOSING);
      }
    });
  }

  private static void appendMethodParameters(StringBuffer buf, PsiMethod method, boolean showNames) {
    PsiParameter[] params = method.getParameterList().getParameters();
    buf.append('(');
    for (int i = 0; i < params.length; i++) {
      if (i != 0) buf.append(", ");
      PsiParameter param = params[i];
      buf.append(param.getType().getPresentableText());
      if (showNames) {
        buf.append(' ');
        buf.append(param.getName());
      }
    }
    buf.append(')');
  }

  private static void appendQualifiedName(StringBuffer buf, RefEntity refEntity) {
    String qName = "";

    while (!(refEntity instanceof RefProject)) {
      if (qName.length() > 0) qName = "." + qName;

      final String name;
      if (refEntity instanceof RefMethod) {
        PsiMethod psiMethod = (PsiMethod)((RefMethod)refEntity).getElement();
        if (psiMethod != null) {
          name = psiMethod.getName();
        }
        else {
          name = refEntity.getName();
        }
      }
      else {
        name = refEntity.getName();
      }

      qName = name + qName;
      refEntity = refEntity.getOwner();
    }

    buf.append(qName);
  }

  private void appendElementReference(final StringBuffer buf, RefElement refElement) {
    appendElementReference(buf, refElement, true);
  }

  public void appendElementReference(final StringBuffer buf, RefElement refElement, String linkText, @NonNls String frameName) {    
    if (myExporter == null) {
      appendElementReference(buf, ((RefElementImpl) refElement).getURL().toString(), linkText, frameName);
    }
    else {
      appendElementReference(buf, myExporter.getURL(refElement), linkText, frameName);
    }
  }

  public void appendElementReference(final StringBuffer buf, String url, String linkText, @NonNls String frameName) {
    buf.append(A_HREF_OPENING);
    buf.append(url);
    if (frameName != null) {
      @NonNls final String target = "\" target=\"";
      buf.append(target);
      buf.append(frameName);
    }

    buf.append("\">");
    buf.append(linkText);
    buf.append(A_CLOSING);
  }

  protected void appendQuickFix(@NonNls final StringBuffer buf, String text, int index) {
    if (myExporter == null) {
      buf.append(FONT_OPENING);
      buf.append("<a HREF=\"file://bred.txt#invoke:" + index);
      buf.append("\">");
      buf.append(text);
      buf.append("</a></font>");
    }
  }

  protected void appendElementReference(final StringBuffer buf, RefElement refElement, boolean isPackageIncluded) {
    appendElementReference(buf, refElement, isPackageIncluded, null);
  }

  private void appendElementReference(final StringBuffer buf,
                                      RefElement refElement,
                                      boolean isPackageIncluded,
                                      String frameName) {
    if (refElement instanceof RefImplicitConstructor) {
      buf.append(InspectionsBundle.message("inspection.export.results.implicit.constructor"));
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append(CODE_OPENING);
    if (refElement instanceof RefField) {
      RefField field = (RefField)refElement;
      PsiField psiField = field.getElement();
      buf.append(psiField.getType().getPresentableText());
      buf.append(NBSP);
    }
    else if (refElement instanceof RefMethod) {
      RefMethod method = (RefMethod)refElement;
      PsiMethod psiMethod = (PsiMethod)method.getElement();
      PsiType returnType = psiMethod.getReturnType();

      if (returnType != null) {
        buf.append(returnType.getPresentableText());
        buf.append(NBSP);
      }
    }

    buf.append(A_HREF_OPENING);

    if (myExporter == null) {
      buf.append(((RefElementImpl) refElement).getURL());
    }
    else {
      buf.append(myExporter.getURL(refElement));
    }

    if (frameName != null) {
      @NonNls final String target = "\" target=\"";
      buf.append(target);
      buf.append(frameName);
    }

    buf.append("\">");


    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) {
      buf.append(InspectionsBundle.message("inspection.reference.anonymous"));
    }
    else if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)refElement.getElement();
      buf.append(psiMethod.getName());
    }
    else {
      buf.append(refElement.getName());
    }

    buf.append(A_CLOSING);

    if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)refElement.getElement();
      appendMethodParameters(buf, psiMethod, false);
    }

    buf.append(CODE_CLOSING);

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) {
      buf.append(" ");
      buf.append(InspectionsBundle.message("inspection.export.results.anonymous.ref.in.owner"));
      buf.append(" ");
      appendElementReference(buf, ((RefElement)refElement.getOwner()), isPackageIncluded);
    }
    else if (isPackageIncluded) {
      @NonNls final String color = "color:#808080\">";
      buf.append(" ").append(CODE_OPENING).append(FONT_OPENING).append(color).append("(");
      appendQualifiedName(buf, refElement.getOwner());
//      buf.append(RefUtil.getPackageName(refElement));
      buf.append(")").append(FONT_CLOSING).append(CODE_CLOSING);
    }
  }

  protected static String appendNumereable(int n,
                                           String statement,
                                           String singleEnding,
                                           String multipleEnding) {
    StringBuffer buf = new StringBuffer();
    buf.append(n);
    buf.append(' ');
    buf.append(statement);

    if (n % 10 == 1 && n % 100 != 11) {
      buf.append(singleEnding);
    }
    else {
      buf.append(multipleEnding);
    }
    return buf.toString();
  }

  protected void appendElementInReferences(StringBuffer buf, RefElement refElement) {
    if (refElement.getInReferences().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.used.from"));
      startList();
      for (Iterator<RefElement> iterator = refElement.getInReferences().iterator(); iterator.hasNext();) {
        RefElement refCaller = iterator.next();
        appendListItem(buf, refCaller);
      }
      doneList(buf);
    }
  }

  protected void appendElementOutReferences(StringBuffer buf, RefElement refElement) {
    if (refElement.getOutReferences().size() > 0) {
      buf.append(BR);
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.uses"));
      startList();
      for (Iterator<RefElement> iterator = refElement.getOutReferences().iterator(); iterator.hasNext();) {
        RefElement refCallee = iterator.next();
        appendListItem(buf, refCallee);
      }
      doneList(buf);
    }
  }

  protected void appendListItem(StringBuffer buf, RefElement refElement) {
    startListItem(buf);
    buf.append(FONT_OPENING);
    appendElementReference(buf, refElement, true);
    appendAdditionalListItemInfo(buf, refElement);
    buf.append(FONT_CLOSING);
    doneListItem(buf);
  }

  protected void appendAdditionalListItemInfo(StringBuffer buf, RefElement refElement) {
    // Default appends nothing.
  }

  protected void appendClassExtendsImplements(StringBuffer buf, RefClass refClass) {
    if (refClass.getBaseClasses().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.extends.implements"));
      startList();
      for (Iterator<RefClass> iterator = refClass.getBaseClasses().iterator(); iterator.hasNext();) {
        RefClass refBase = iterator.next();
        appendListItem(buf, refBase);
      }
      doneList(buf);
    }
  }

  protected void appendDerivedClasses(StringBuffer buf, RefClass refClass) {
    if (refClass.getSubClasses().size() > 0) {
      if (refClass.isInterface()) {
        appendHeading(buf, InspectionsBundle.message("inspection.export.results.extended.implemented"));
      }
      else {
        appendHeading(buf, InspectionsBundle.message("inspection.export.results.extended"));
      }

      startList();
      for (Iterator<RefClass> iterator = refClass.getSubClasses().iterator(); iterator.hasNext();) {
        RefClass refDerived = iterator.next();
        appendListItem(buf, refDerived);
      }
      doneList(buf);
    }
  }

  protected void appendLibraryMethods(StringBuffer buf, RefClass refClass) {
    if (refClass.getLibraryMethods().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.overrides.library.methods"));

      startList();
      for (Iterator<RefMethod> iterator = refClass.getLibraryMethods().iterator(); iterator.hasNext();) {
        RefMethod refMethod = iterator.next();
        appendListItem(buf, refMethod);
      }
      doneList(buf);
    }
  }

  protected void appendSuperMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getSuperMethods().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.overrides.implements"));

      startList();
      for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        appendListItem(buf, refSuper);
      }
      doneList(buf);
    }
  }

  protected void appendDerivedMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getDerivedMethods().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.derived.methods"));

      startList();
      for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
        RefMethod refDerived = iterator.next();
        appendListItem(buf, refDerived);
      }
      doneList(buf);
    }
  }

  protected void appendTypeReferences(StringBuffer buf, RefClass refClass) {
    if (refClass.getInTypeReferences().size() > 0) {
      appendHeading(buf, InspectionsBundle.message("inspection.export.results.type.references"));

      startList();
      for (Iterator iterator = refClass.getInTypeReferences().iterator(); iterator.hasNext();) {
        RefElement refElement = (RefElement)iterator.next();
        appendListItem(buf, refElement);
      }
      doneList(buf);
    }
  }

  protected void appendResolution(StringBuffer buf, InspectionTool tool, RefEntity where) {
    if (myExporter != null) return;
    QuickFixAction[] quickFixes = tool.getQuickFixes(new RefEntity[] {where});
    if (quickFixes != null) {
      boolean listStarted = false;
      for (int i = 0; i < quickFixes.length; i++) {
        QuickFixAction quickFix = quickFixes[i];
        final String text = quickFix.getText(where);
        if (text == null) continue;
        if (!listStarted) {
          appendHeading(buf, InspectionsBundle.message("inspection.problem.resolution"));
          startList();
          listStarted = true;
        }
        startListItem(buf);
        appendQuickFix(buf, text, i);
        doneListItem(buf);
      }

      if (listStarted) {
        doneList(buf);
      }
    }
  }

  protected void startList() {
    myListStackTop++;
    myListStack[myListStackTop] = 0;
  }

  protected void doneList(@NonNls StringBuffer buf) {
    if (myListStack[myListStackTop] != 0) {
      buf.append("<table cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tr><td>&nbsp;</td></tr></table>");
    }
    myListStackTop--;
  }

  protected void startListItem(@NonNls StringBuffer buf) {
    myListStack[myListStackTop]++;
    buf.append("<li>");
  }

  protected static void doneListItem(@NonNls StringBuffer buf) {
    buf.append("</li>");
  }

  public static void appendAfterHeaderIndention(@NonNls StringBuffer buf) {
    buf.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
  }

  protected static void appendNoProblems(StringBuffer buf) {
    buf.append(BR);
    appendAfterHeaderIndention(buf);
    buf.append(B_OPENING);
    buf.append(InspectionsBundle.message("inspection.export.results.no.problems.found"));
    buf.append(B_CLOSING).append(BR);
  }
}