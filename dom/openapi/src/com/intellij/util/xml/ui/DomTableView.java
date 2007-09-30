/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class DomTableView extends AbstractTableView<DomElement> {
  private final List<TypeSafeDataProvider> myCustomDataProviders = new SmartList<TypeSafeDataProvider>();

  public DomTableView(final Project project) {
    super(project);
  }

  public DomTableView(final Project project, final String emptyPaneText, final String helpID) {
    super(project, emptyPaneText, helpID);
  }

  public void addCustomDataProvider(TypeSafeDataProvider provider) {
    myCustomDataProviders.add(provider);
  }

  public void calcData(final DataKey key, final DataSink sink) {
    super.calcData(key, sink);
    for (final TypeSafeDataProvider customDataProvider : myCustomDataProviders) {
      customDataProvider.calcData(key, sink);
    }
  }

  @Deprecated
  protected final void installPopup(final DefaultActionGroup group) {
    installPopup(ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, group);
  }

  protected void wrapValueSetting(@NotNull final DomElement domElement, final Runnable valueSetter) {
    if (domElement.isValid()) {
      new WriteCommandAction(getProject(), domElement.getRoot().getFile()) {
        protected void run(final Result result) throws Throwable {
          valueSetter.run();
        }
      }.execute();
      fireChanged();
    }
  }

}
