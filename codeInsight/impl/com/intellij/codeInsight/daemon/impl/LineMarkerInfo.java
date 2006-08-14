package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public class LineMarkerInfo {
  public static final LineMarkerInfo[] EMPTY_ARRAY = new LineMarkerInfo[0];

  public static final int OVERRIDING_METHOD = 1;
  public static final int OVERRIDEN_METHOD = 2;
  public static final int METHOD_SEPARATOR = 5;
  public static final int SUBCLASSED_CLASS = 6;

  public final int type;
  private Icon myIcon;
  public final WeakReference<PsiElement> elementRef;
  public final int startOffset;
  public TextAttributes attributes;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;

  public LineMarkerInfo(int type, PsiElement element, int startOffset, Icon icon) {
    this.type = type;
    myIcon = icon;
    elementRef = new WeakReference<PsiElement>(element);
    this.startOffset = startOffset;
  }

  public GutterIconRenderer createGutterRenderer() {
    if (myIcon == null) return null;
    return new GutterIconRenderer() {
      @NotNull
      public Icon getIcon() {
        return myIcon;
      }

      public AnAction getClickAction() {
        return new NavigateAction();
      }

      public boolean isNavigateAction() {
        return true;
      }

      public String getTooltipText() {
        return getLineMarkerTooltip();
      }

      public GutterIconRenderer.Alignment getAlignment() {
        boolean isImplements = type == OVERRIDING_METHOD;
        return isImplements ? GutterIconRenderer.Alignment.LEFT : GutterIconRenderer.Alignment.RIGHT;
      }
    };
  }

  private String getLineMarkerTooltip() {
    PsiElement element = elementRef.get();
    if (element == null || !element.isValid()) return null;
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      return getMethodTooltip(method);
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      return getClassTooltip(aClass);
    }
    return null;
  }

  private String getMethodTooltip(PsiMethod method) {
    if (type == OVERRIDING_METHOD){
      PsiMethod[] superMethods = method.findSuperMethods(false);
      if (superMethods.length == 0) return null;

      PsiMethod superMethod = superMethods[0];
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
      boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

      final boolean sameSignature = superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY));
      @NonNls final String key;
      if (isSuperAbstract && !isAbstract){
        key = sameSignature ? "method.implements" : "method.implements.in";
      }
      else{
        key = sameSignature ? "method.overrides" : "method.overrides.in";
      }
      return composeText(superMethods, "", DaemonBundle.message(key));
    }
    else if (type == OVERRIDEN_METHOD){
      PsiManager manager = method.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);
      helper.processOverridingMethods(processor, method, GlobalSearchScope.allScope(manager.getProject()), true);

      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

      if (processor.isOverflow()){
        return isAbstract ? DaemonBundle.message("method.is.implemented.too.many") : DaemonBundle.message("method.is.overridden.too.many");
      }

      PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
      if (overridings.length == 0) return null;

      Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
      Arrays.sort(overridings, comparator);

      String start = isAbstract ? DaemonBundle.message("method.is.implemented.header") : DaemonBundle.message("method.is.overriden.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
      return composeText(overridings, start, pattern);
    }
    else{
      return null;
    }
  }

  private String getClassTooltip(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    if (type == SUBCLASSED_CLASS) {
      PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5);
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      helper.processInheritors(processor, aClass, scope, true);

      if (processor.isOverflow()) {
        return aClass.isInterface()
               ? DaemonBundle.message("interface.is.implemented.too.many")
               : DaemonBundle.message("class.is.subclassed.too.many");
      }

      PsiClass[] subclasses = processor.toArray(new PsiClass[processor.getCollection().size()]);
      if (subclasses.length == 0) return null;

      Comparator<PsiClass> comparator = new PsiClassListCellRenderer().getComparator();
      Arrays.sort(subclasses, comparator);

      String start = aClass.isInterface()
                     ? DaemonBundle.message("interface.is.implemented.by.header")
                     : DaemonBundle.message("class.is.subclassed.by.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
      return composeText(subclasses, start, pattern);
    }

    return null;
  }

  private class NavigateAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      MouseEvent mousEvent = (MouseEvent) e.getInputEvent();
      LineMarkerNavigator.browse(mousEvent, LineMarkerInfo.this);
    }
  }

  private static String composeText(PsiElement[] elements, String start, String formatPattern) {
    @NonNls StringBuilder result = new StringBuilder();
    result.append("<html><body>");
    result.append(start);
    Set<String> names = new LinkedHashSet<String>();
    for (PsiElement element : elements) {
      String descr = "";
      if (element instanceof PsiClass) {
        String className = ClassPresentationUtil.getNameForClass((PsiClass)element, true);
        descr = MessageFormat.format(formatPattern, className);
      }
      else if (element instanceof PsiMethod) {
        String methodName = ((PsiMethod)element).getName();
        String className = ClassPresentationUtil.getNameForClass(((PsiMethod)element).getContainingClass(), true);
        descr = MessageFormat.format(formatPattern, methodName, className);
      }
      else if (element instanceof PsiFile) {
        descr = MessageFormat.format(formatPattern, ((PsiFile)element).getName());
      }
      names.add(descr);
    }

    @NonNls String sep = "";
    for (String name : names) {
      result.append(sep);
      sep = "<br>";
      result.append(name);
    }

    result.append("</body></html>");
    return result.toString();
  }
}