// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.handler;

import com.intellij.internal.jcef.test.detailed.dialog.SearchDialog;
import com.intellij.internal.jcef.test.detailed.dialog.ShowTextDialog;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefMenuModel.MenuId;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;


@ApiStatus.Internal
public class  ContextMenuHandler extends CefContextMenuHandlerAdapter {
    private final Frame owner_;
    private final Map<Integer, String> suggestions_ = new HashMap<>();

    public ContextMenuHandler(Frame owner) {
        owner_ = owner;
    }

    @Override
    public void onBeforeContextMenu(
            CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        model.clear();

        // Navigation menu
        model.addItem(MenuId.MENU_ID_BACK, "Back");
        model.setEnabled(MenuId.MENU_ID_BACK, browser.canGoBack());

        model.addItem(MenuId.MENU_ID_FORWARD, "Forward");
        model.setEnabled(MenuId.MENU_ID_FORWARD, browser.canGoForward());

        model.addSeparator();
        model.addItem(MenuId.MENU_ID_FIND, "Find...");
        if (params.hasImageContents() && params.getSourceUrl() != null)
            model.addItem(MenuId.MENU_ID_USER_FIRST, "Download Image...");
        model.addItem(MenuId.MENU_ID_VIEW_SOURCE, "View Source...");

        model.addItem(MenuId.MENU_ID_NO_SPELLING_SUGGESTIONS, "Suggestions isn't implemented yet.");
        model.setEnabled(MenuId.MENU_ID_NO_SPELLING_SUGGESTIONS, false);

        //NOTE: uncomment to support DictionarySuggestions
        //Vector<String> suggestions = new Vector<>();
        //params.getDictionarySuggestions(suggestions);
        //
        //// Spell checking menu
        //model.addSeparator();
        //if (suggestions.isEmpty()) {
        //    model.addItem(MenuId.MENU_ID_NO_SPELLING_SUGGESTIONS, "No suggestions");
        //    model.setEnabled(MenuId.MENU_ID_NO_SPELLING_SUGGESTIONS, false);
        //    return;
        //}
        //
        //int id = MenuId.MENU_ID_SPELLCHECK_SUGGESTION_0;
        //for (String suggestedWord : suggestions) {
        //    model.addItem(id, suggestedWord);
        //    suggestions_.put(id, suggestedWord);
        //    if (++id > MenuId.MENU_ID_SPELLCHECK_SUGGESTION_LAST) break;
        //}
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame,
            CefContextMenuParams params, int commandId, int eventFlags) {
        switch (commandId) {
            case MenuId.MENU_ID_VIEW_SOURCE:
                ShowTextDialog visitor =
                        new ShowTextDialog(owner_, "Source of \"" + browser.getURL() + "\"");
                browser.getSource(visitor);
                return true;
            case MenuId.MENU_ID_FIND:
                SearchDialog search = new SearchDialog(owner_, browser);
                search.setVisible(true);
                return true;
            case MenuId.MENU_ID_USER_FIRST:
                browser.startDownload(params.getSourceUrl());
                return true;
            default:
                if (commandId >= MenuId.MENU_ID_SPELLCHECK_SUGGESTION_0) {
                    String newWord = suggestions_.get(commandId);
                    if (newWord != null) {
                        browser.replaceMisspelling(newWord);
                        return true;
                    }
                }
                return false;
        }
    }

    @Override
    public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
        suggestions_.clear();
    }
}
