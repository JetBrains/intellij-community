// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBHtmlEditorKit;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

import static com.intellij.codeInsight.documentation.DocumentationComponent.SECTION_COLOR;

final class DocumentationHtmlEditorKit extends JBHtmlEditorKit {

  private final @NotNull Component myReferenceComponent;

  DocumentationHtmlEditorKit(@NotNull Component referenceComponent) {
    super(true, true);
    myReferenceComponent = referenceComponent;
    prepareCSS(this);
  }

  @Override
  public ViewFactory getViewFactory() {
    JBHtmlFactory factory = new JBHtmlFactory() {
      @Override
      public View create(Element elem) {
        View view = super.create(elem);
        if (view instanceof ImageView) {
          // we have to work with raw image, apply scaling manually
          return new DocumentationScalingImageView(elem, myReferenceComponent);
        }
        return view;
      }
    };
    factory.setAdditionalIconResolver(src -> {
      ModuleType<?> id = ModuleTypeManager.getInstance().findByID(src);
      return id == null ? null : id.getIcon();
    });
    return factory;
  }

  private static void prepareCSS(@NotNull JBHtmlEditorKit editorKit) {
    editorKit.setFontResolver(EditorCssFontResolver.getGlobalInstance());

    int leftPadding = 8;
    int definitionTopPadding = 4;
    String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    String borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor());
    String sectionColor = ColorUtil.toHtmlColor(SECTION_COLOR);
    String editorFontStyle = "{font-family:\"" + EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER + "\"; font-size: 96%;}";

    StyleSheet styleSheet = editorKit.getStyleSheet();
    styleSheet.addRule("tt" + editorFontStyle);
    styleSheet.addRule("code" + editorFontStyle);
    styleSheet.addRule("pre" + editorFontStyle);
    styleSheet.addRule(".pre" + editorFontStyle);

    styleSheet.addRule("html { padding-bottom: 8px; }");
    styleSheet.addRule("h1, h2, h3, h4, h5, h6 { margin-top: 0; padding-top: 1px; }");
    styleSheet.addRule("a { color: " + linkColor + "; text-decoration: none;}");
    styleSheet.addRule(".definition { padding: " + definitionTopPadding + "px 17px 1px " + leftPadding +
                       "px; border-bottom: thin solid " + borderColor + "; }");
    styleSheet.addRule(".definition-only { padding: " + definitionTopPadding + "px 17px 0 " + leftPadding + "px; }");
    styleSheet.addRule(".definition-only pre { margin-bottom: 0 }");
    styleSheet.addRule(".content { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }");
    styleSheet.addRule(".content-separated { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
                       "                     border-bottom: thin solid " + borderColor + "; }");
    styleSheet.addRule(".content-only { padding: 8px 16px 0 " + leftPadding + "px; max-width: 100% }");
    styleSheet.addRule(".bottom { padding: 3px 16px 0 " + leftPadding + "px; }");
    styleSheet.addRule(".bottom-no-content { padding: 5px 16px 0 " + leftPadding + "px; }");
    styleSheet.addRule("p { padding: 1px 0 2px 0; }");
    styleSheet.addRule("ol { padding: 0 16px 0 0; }");
    styleSheet.addRule("ul { padding: 0 16px 0 0; }");
    styleSheet.addRule("li { padding: 1px 0 2px 0; }");
    styleSheet.addRule(".grayed { color: #909090; display: inline;}");
    styleSheet.addRule(".centered { text-align: center}");

    // sections table
    styleSheet.addRule(".sections { padding: 0 16px 0 " + leftPadding + "px; border-spacing: 0; }");
    styleSheet.addRule("tr { margin: 0 0 0 0; padding: 0 0 0 0; }");
    styleSheet.addRule("table p { padding-bottom: 0}");
    styleSheet.addRule("td { margin: 4px 0 0 0; padding: 0 0 0 0; }");
    styleSheet.addRule("th { text-align: left; }");
    styleSheet.addRule("td pre { padding: 1px 0 0 0; margin: 0 0 0 0 }");
    styleSheet.addRule(".section { color: " + sectionColor + "; padding-right: 4px; white-space:nowrap;}");
  }
}
