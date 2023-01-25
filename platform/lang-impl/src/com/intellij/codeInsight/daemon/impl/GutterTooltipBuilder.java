// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.openapi.keymap.KeymapUtil.getPreferredShortcutText;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;
import static com.intellij.ui.ColorUtil.toHex;

public abstract class GutterTooltipBuilder {
  private static final JBColor SEPARATOR_COLOR = JBColor.namedColor("GutterTooltip.lineSeparatorColor", HintUtil.INFORMATION_BORDER_COLOR);
  private static final JBColor CONTEXT_HELP_FOREGROUND
    = JBColor.namedColor("GutterTooltip.infoForeground", new JBColor(0x787878, 0x878787));

  @NotNull
  protected abstract String getLinkProtocol();

  @Nullable
  protected abstract String getLinkReferenceText(@NotNull PsiElement element);

  @Nullable
  protected abstract PsiElement getContainingElement(@NotNull PsiElement element);

  protected abstract boolean shouldSkipAsFirstElement(@NotNull PsiElement element);

  @Nullable
  protected abstract String getPresentableName(@NotNull PsiElement element);

  @Nullable
  protected String getLocationString(@NotNull PsiElement element) {
    return null;
  }


  /**
   * @param elements        a collection of elements to create a formatted tooltip text
   * @param prefix          a text to insert before all elements
   * @param skipFirstMember {@code true} to skip a method (or field) name in the link to element
   * @param actionId        an action identifier to generate context help or {@code null} if not applicable
   */
  @NotNull
  public <E extends PsiElement> String buildTooltipText(@NotNull Collection<E> elements,
                                                        @NotNull String prefix,
                                                        boolean skipFirstMember,
                                                        @Nullable String actionId) {
    return buildTooltipText(elements, prefix, skipFirstMember, actionId, "press.to.navigate");
  }

  /**
   * @param elements        a collection of elements to create a formatted tooltip text
   * @param prefix          a text to insert before all elements
   * @param skipFirstMember {@code true} to skip a method (or field) name in the link to element
   * @param actionId        an action identifier to generate context help or {@code null} if not applicable
   * @param pressMessageKey JavaBundle key to retrieve context help message with shortcut
   */
  @NotNull
  protected <E extends PsiElement> String buildTooltipText(@NotNull Collection<E> elements,
                                                           @NotNull String prefix,
                                                           boolean skipFirstMember,
                                                           @Nullable String actionId,
                                                           @NotNull String pressMessageKey) {
    String firstDivider = getElementDivider(true, true, elements.size());
    String nextDivider = getElementDivider(false, true, elements.size());
    AtomicReference<String> reference = new AtomicReference<>(firstDivider); // optimization: calculate next divider only once
    return buildTooltipText(prefix, elements, e -> reference.getAndSet(nextDivider), e -> skipFirstMember, actionId, pressMessageKey);
  }

  public static String getElementDivider(boolean firstElement, boolean marginLeft, int elementsCount) {
    if (elementsCount <= 1) return " ";
    StringBuilder sb = new StringBuilder("</p><p style='margin-top:2pt");
    if (marginLeft) sb.append(";margin-left:20pt");
    if (!firstElement) sb.append(";border-top:thin solid #").append(toHex(SEPARATOR_COLOR));
    return sb.append(";'>").toString();
  }

  /**
   * @param elements                 a collection of elements to create a formatted tooltip text
   * @param elementToPrefix          a function that returns a text to insert before the current element
   * @param skipFirstMemberOfElement a function that returns {@code true} to skip a method (or field) name for the current element
   * @param actionId                 an action identifier to generate context help or {@code null} if not applicable
   */
  @NotNull
  public <E extends PsiElement> String buildTooltipText(@NotNull Collection<? extends E> elements,
                                                           @NotNull Function<? super E, String> elementToPrefix,
                                                           @NotNull Predicate<? super E> skipFirstMemberOfElement,
                                                           @Nullable String actionId) {
    return buildTooltipText(null, elements, elementToPrefix, skipFirstMemberOfElement, actionId, "press.to.navigate");
  }

  @NotNull
  protected  <E extends PsiElement> String buildTooltipText(@Nullable String prefix,
                                                         @NotNull Collection<? extends E> elements,
                                                         @NotNull Function<? super E, String> elementToPrefix,
                                                         @NotNull Predicate<? super E> skipFirstMemberOfElement,
                                                         @Nullable String actionId,
                                                         @NotNull String pressMessageKey) {
    StringBuilder sb = new StringBuilder("<html><body><p>");
    if (prefix != null) sb.append(prefix);
    Set<String> names = new HashSet<>();
    for (E element : elements) {
      StringBuilder elementBuilder = new StringBuilder();
      appendElement(elementBuilder, element, skipFirstMemberOfElement.test(element));
      if (names.add(elementBuilder.toString())) {
        String elementPrefix = elementToPrefix.apply(element);
        if (elementPrefix != null) sb.append(elementPrefix);
        sb.append(elementBuilder);
      }
    }
    appendContextHelp(sb, actionId, pressMessageKey);
    sb.append("</p></body></html>");
    return sb.toString();
  }

  protected void appendElement(@NotNull StringBuilder sb, @NotNull PsiElement element, boolean skip) {
    boolean useSingleLink = Registry.is("gutter.tooltip.single.link");
    boolean addedSingleLink = useSingleLink && appendLink(sb, element);
    String locationString = getLocationString(element);
    PsiElement original = element; // use original member as a first separate link
    if (skip && shouldSkipAsFirstElement(element)) {
      element = getContainingElement(element);
    }
    while (element != null) {
      String name = getPresentableName(element);
      if (name != null) {
        boolean addedLink = !useSingleLink && appendLink(sb, original != null ? original : element);
        original = null; // do not use a link to the original element if it is already added
        // Swing uses simple HTML processing and paints a link incorrectly if it contains different fonts.
        // This is the reason why I use monospaced font not only for element name, but for a whole link.
        // By the same reason I have to comment out support for deprecated elements.
        //
        // boolean deprecated = RefJavaUtil.isDeprecated(element);
        // if (deprecated) sb.append("<strike>");
        // sb.append("<code>");
        sb.append(name);
        // sb.append("</code>");
        // if (deprecated) sb.append("</strike>");
        if (addedLink) sb.append("</code></a>");
      }
      PsiElement parent = element instanceof PsiFile? null : getContainingElement(element);
      if (parent == null || parent instanceof PsiFile) {
        if (locationString != null) {
          sb.append(locationString);
        }
        break;
      }
      if (name != null) sb.append(" ").append(LangBundle.message("tooltip.in")).append(" ");
      element = parent;
    }
    if (addedSingleLink) sb.append("</code></a>");
  }

  protected static void appendPackageName(@NotNull StringBuilder sb, @Nullable String name) {
    if (StringUtil.isEmpty(name)) return; // no package name
    sb.append(" <font color='#").append(toHex(CONTEXT_HELP_FOREGROUND));
    sb.append("'><code>(").append(name).append(")</code></font>");
  }

  private static void appendContextHelp(@NotNull StringBuilder sb, @Nullable String actionId, String key) {
    if (actionId == null) return; // action id is not set
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) return; // action is not exist
    String text = getPreferredShortcutText(action.getShortcutSet().getShortcuts());
    if (StringUtil.isEmpty(text)) return; // action have no shortcuts
    sb.append("</p><p style='margin-top:8px;'><font size='2' color='#");
    sb.append(toHex(CONTEXT_HELP_FOREGROUND));
    sb.append("'>").append(LangBundle.message(key, text)).append("</font>");
  }

  protected boolean appendLink(@NotNull StringBuilder sb, @NotNull PsiElement element) {
    try {
      String name = getLinkReferenceText(element);
      if (!StringUtil.isEmpty(name)) {
        sb.append("<a href=\"#").append(getLinkProtocol()).append("/").append(name).append("\"><code>");
        return true;
      }
      VirtualFile file = getVirtualFile(element);
      if (file == null) return false;

      int offset = element.getTextOffset();
      sb.append("<a href=\"#navigation/");
      sb.append(toSystemIndependentName(file.getPath()));
      sb.append(":").append(offset).append("\"><code>");
      return true;
    }
    catch (Exception ignored) {
      return false;
    }
  }
}
