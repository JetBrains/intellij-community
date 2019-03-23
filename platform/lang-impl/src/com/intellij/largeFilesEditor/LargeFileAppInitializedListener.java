// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.largeFilesEditor.actions.LfeActionDisabled;
import com.intellij.largeFilesEditor.actions.LfeActionNextOccurence;
import com.intellij.largeFilesEditor.actions.LfeActionPrevOccurrence;
import com.intellij.largeFilesEditor.actions.LfeBaseProxyAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;

public class LargeFileAppInitializedListener implements ApplicationInitializedListener {

  private static final Logger logger = Logger.getInstance(LargeFileAppInitializedListener.class);

  @Override
  public void componentsInitialized() {
    replaceActionByProxy("FindNext", LfeActionNextOccurence::new);
    replaceActionByProxy("FindPrevious", LfeActionPrevOccurrence::new);
    disableActionForLfe("FindUsagesInFile");
    logger.warn("LFE: actions replaced by proxies");
  }

  private void disableActionForLfe(String actionId) {
    replaceActionByProxy(actionId, LfeActionDisabled::new);
  }

  private void replaceActionByProxy(String actionId, LfeActionFactory lfeActionFactory) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction originalAction = actionManager.getAction(actionId);
    if (originalAction == null) {
      logger.warn("Can't replace action with id=\"" + actionId + "\". Action with this id doesn't exist");
      return;
    }

    AnAction proxyAction = lfeActionFactory.create(originalAction);

    // TODO: 29.11.18 use ActionManager::replaceAction after moving "since" build to 191
    actionManager.unregisterAction(actionId);
    actionManager.registerAction(actionId, proxyAction);
    logger.info("Replaced action with id=\"" + actionId + "\" by class: \"" + proxyAction.getClass().getName() + "\"");
  }


  private interface LfeActionFactory<T extends LfeBaseProxyAction> {
    T create(AnAction originalAction);
  }
}
