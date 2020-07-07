// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class WhatsNewAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String text = e.getPresentation().getText();
    String whatsNewUrl = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl();
    if (e.getProject() == null || text == null) {
      BrowserUtil.browse(whatsNewUrl);
    } else {
      String url = whatsNewUrl + getEmbeddedSuffix();
      HTMLEditorProvider.Companion.openEditor(e.getProject(), text, url, null, getTimeoutCallback(whatsNewUrl));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() != null;
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation()
        .setText(IdeBundle.messagePointer("whatsnew.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName()));
      e.getPresentation().setDescription(
        IdeBundle.messagePointer("whatsnew.action.custom.description", ApplicationNamesInfo.getInstance().getFullProductName()));
    }
  }

  @NotNull
  public static String getEmbeddedSuffix() {
    return "?var=embed" + (UIUtil.isUnderDarcula() ? "&theme=dark" : "");
  }

  @NotNull
  private static String getTimeoutCallback(@NotNull String whatsNewUrl) {
    return "<!DOCTYPE html>\n" +
           "<html lang=\"en\">\n" +
           "    <head>\n" +
           "        <title>Whats'new page</title>\n" +
           "        <meta charset=\"utf-8\" />\n" +
           "        <meta http-equiv=\"x-ua-compatible\" content=\"IE=edge\" />\n" +
           "        <meta name=\"viewport\" content=\"width=device-width, maximum-scale=1\" />\n" +
           "\n" +
           "        <style>\n" +
           "            *,\n" +
           "            *::after {\n" +
           "                box-sizing: border-box;\n" +
           "            }\n" +
           "\n" +
           "            html,\n" +
           "            body,\n" +
           "            p,\n" +
           "            h3,\n" +
           "            h4 {\n" +
           "                margin: 0;\n" +
           "                padding: 0;\n" +
           "            }\n" +
           "\n" +
           "            html,\n" +
           "            body {\n" +
           "                height: 100%;\n" +
           "            }\n" +
           "\n" +
           "            body {\n" +
           "                background-color: #fff;\n" +
           "            }\n" +
           "\n" +
           "            .container {\n" +
           "                box-sizing: border-box;\n" +
           "                width: 100%;\n" +
           "                max-width: 1276px;\n" +
           "                margin-right: auto;\n" +
           "                margin-left: auto;\n" +
           "                padding-right: 22px;\n" +
           "                padding-left: 22px;\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 1276px) {\n" +
           "                .container {\n" +
           "                    max-width: 996px;\n" +
           "                    padding-right: 22px;\n" +
           "                    padding-left: 22px;\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 1000px) {\n" +
           "                .container {\n" +
           "                    max-width: 100%;\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 640px) {\n" +
           "                .container {\n" +
           "                    padding-right: 16px;\n" +
           "                    padding-left: 16px;\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            .content {\n" +
           "                width: calc(100% / 12 * 6);\n" +
           "                margin-left: calc(100% / 12 * 3);\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 1276px) {\n" +
           "                .content {\n" +
           "                    width: calc(100% / 12 * 8);\n" +
           "                    margin-left: calc(100% / 12 * 2);\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 1000px) {\n" +
           "                .content {\n" +
           "                    width: calc(100% / 12 * 10);\n" +
           "                    margin-left: calc(100% / 12 * 1);\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 640px) {\n" +
           "                .content {\n" +
           "                    width: calc(100% / 12 * 12);\n" +
           "                    margin-left: calc(100% / 12 * 0);\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            .text {\n" +
           "                letter-spacing: normal;\n" +
           "\n" +
           "                color: rgba(39, 40, 44, 0.7);\n" +
           "\n" +
           "                font-family: system-ui, -apple-system, BlinkMacSystemFont,\n" +
           "                    'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Droid Sans',\n" +
           "                    'Helvetica Neue', Arial, sans-serif;\n" +
           "                font-size: 15px;\n" +
           "                font-weight: normal;\n" +
           "                font-style: normal;\n" +
           "                font-stretch: normal;\n" +
           "                line-height: 1.6;\n" +
           "            }\n" +
           "\n" +
           "            .title {\n" +
           "                letter-spacing: normal;\n" +
           "\n" +
           "                color: #27282c;\n" +
           "\n" +
           "                font-family: -apple-system, Helvetica, system-ui,\n" +
           "                    BlinkMacSystemFont, Segoe UI, Roboto, Oxygen, Ubuntu,\n" +
           "                    Cantarell, Droid Sans, Helvetica Neue, Arial, sans-serif;\n" +
           "                font-weight: bold;\n" +
           "                font-style: normal;\n" +
           "                font-stretch: normal;\n" +
           "            }\n" +
           "\n" +
           "            .title_h2 {\n" +
           "                font-size: 31px;\n" +
           "                line-height: 1.3;\n" +
           "            }\n" +
           "\n" +
           "            .title_h3 {\n" +
           "                font-size: 20px;\n" +
           "                line-height: 1.4;\n" +
           "            }\n" +
           "\n" +
           "            .title_h4 {\n" +
           "                font-size: 15px;\n" +
           "                line-height: 1.6;\n" +
           "            }\n" +
           "\n" +
           "            .offset-12 {\n" +
           "                margin-top: 12px;\n" +
           "            }\n" +
           "\n" +
           "            .offset-24 {\n" +
           "                margin-top: 24px;\n" +
           "            }\n" +
           "\n" +
           "            .offset-48 {\n" +
           "                margin-top: 48px;\n" +
           "            }\n" +
           "\n" +
           "            .offset-96 {\n" +
           "                margin-top: 96px;\n" +
           "            }\n" +
           "\n" +
           "            @media screen and (max-width: 640px) {\n" +
           "                .offset-12 {\n" +
           "                    margin-top: 8px;\n" +
           "                }\n" +
           "\n" +
           "                .offset-24 {\n" +
           "                    margin-top: 16px;\n" +
           "                }\n" +
           "\n" +
           "                .offset-48 {\n" +
           "                    margin-top: 32px;\n" +
           "                }\n" +
           "\n" +
           "                .offset-96 {\n" +
           "                    margin-top: 64px;\n" +
           "                }\n" +
           "            }\n" +
           "\n" +
           "            .link {\n" +
           "                outline: none;\n" +
           "                cursor: pointer;\n" +
           "                font-size: inherit;\n" +
           "                line-height: inherit;\n" +
           "                border-bottom: 1px solid transparent;\n" +
           "            }\n" +
           "\n" +
           "            .link,\n" +
           "            .link:hover {\n" +
           "                text-decoration: none;\n" +
           "            }\n" +
           "\n" +
           "            .link:hover {\n" +
           "                border-bottom-color: currentColor;\n" +
           "            }\n" +
           "\n" +
           "            .link,\n" +
           "            .link:hover,\n" +
           "            .link:active,\n" +
           "            .link:focus {\n" +
           "                color: #167dff;\n" +
           "            }\n" +
           "\n" +
           "            .section {\n" +
           "                padding-bottom: 48px;\n" +
           "                padding-top: 1px;\n" +
           "                background: #fff;\n" +
           "            }\n" +
           "\n" +
           "            .theme-dark, .theme-dark .section {\n" +
           "                background: #27282c;\n" +
           "            }\n" +
           "            .theme-dark .title {\n" +
           "                color: #fff;\n" +
           "            }\n" +
           "            .theme-dark .text {\n" +
           "                color: rgba(255, 255, 255, 0.60);\n" +
           "            }\n" +
           "            .theme-dark .link {\n" +
           "                color: rgb(76, 166, 255);\n" +
           "            }\n" +
           "        </style>\n" +
           "    </head>\n" +
           "    <body class=\"" + (UIUtil.isUnderDarcula() ? "theme-dark" : "") + "\">\n" +
           "        <section class=\"section\">\n" +
           "            <div class=\"container\">\n" +
           "                <div class=\"content\">\n" +
           "                    <h2 class=\"title title_h2 offset-24\">\n" +
           "                        The page cannot be loaded\n" +
           "                    </h2>\n" +
           "\n" +
           "                    <p class=\"text offset-24\">\n" +
           "                        Content for this page cannot be loaded. Please check\n" +
           "                        your internet connection.\n" +
           "                    </p>\n" +
           "\n" +
           "                    <p class=\"text offset-12\">\n" +
           "                        You can\n" +
           "                        <a\n" +
           "                            href=\"" + whatsNewUrl + "\"\n" +
           "                            class=\"link\"\n" +
           "                            target=\"_blank\"\n" +
           "                            >open this page in browser</a\n" +
           "                        >\n" +
           "                        or try again later.\n" +
           "                    </p>\n" +
           "                </div>\n" +
           "            </div>\n" +
           "        </section>\n" +
           "    </body>\n" +
           "</html>";
  }
}
