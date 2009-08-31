package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;

public interface HistoryAware {


  interface Config {

    void setHistoryFacade(Facade facade);

  }

  interface Facade {

    boolean isHistoryNavigatedNow();

    History getHistory();

    ActionCallback select(Configurable configurable);

  }

}
