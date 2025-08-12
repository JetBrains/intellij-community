// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.MarkupModelWindow;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages per-project markup models of documents.
 */
public final class DocumentMarkupModel {
  private static final Key<MarkupModelEx> MARKUP_MODEL_KEY = Key.create("DocumentMarkupModel.MarkupModel");
  private static final Key<ConcurrentMap<Project, MarkupModelImpl>> MARKUP_MODEL_MAP_KEY = Key.create("DocumentMarkupModel.MarkupModelMap");

  private DocumentMarkupModel() {
  }

  /**
   * Returns the markup model for the specified project. A document can have multiple markup
   * models for different projects if the file to which it corresponds belongs to multiple projects
   * opened in different IDEA frames at the same time.
   *
   * @param document the document for which the markup model is requested.
   * @param project the project for which the markup model is requested, or null if the default markup
   *                model is requested.
   * @return the markup model instance.
   * @see com.intellij.openapi.editor.Editor#getMarkupModel()
   */
  @Contract("_,_,true -> !null")
  public static MarkupModel forDocument(@NotNull Document document, @Nullable Project project, boolean create) {
    if (document instanceof DocumentWindow window) {
      Document delegate = window.getDelegate();
      MarkupModelEx baseMarkupModel = (MarkupModelEx)forDocument(delegate, project, true);
      return new MarkupModelWindow(baseMarkupModel, window);
    }

    if (project == null) {
      MarkupModelEx markupModel = document.getUserData(MARKUP_MODEL_KEY);
      if (create && markupModel == null) {
        MarkupModelEx newModel = new MarkupModelImpl((DocumentEx)document);
        fireMarkupModelCreated(null, newModel);
        if ((markupModel = ((UserDataHolderEx)document).putUserDataIfAbsent(MARKUP_MODEL_KEY, newModel)) != newModel) {
          newModel.dispose();
          fireMarkupModelDisposed(null, newModel);
        }
      }
      return markupModel;
    }

    DocumentMarkupModelManager documentMarkupModelManager =
      project.isDisposed() ? null : DocumentMarkupModelManager.getInstance(project);
    if (documentMarkupModelManager == null || documentMarkupModelManager.isDisposed() || project.isDisposed()) {
      return new EmptyMarkupModel(document);
    }

    ConcurrentMap<Project, MarkupModelImpl> markupModelMap = getMarkupModelMap(document);

    MarkupModelImpl model = markupModelMap.get(project);
    if (create && model == null) {
      MarkupModelImpl newModel = new MarkupModelImpl((DocumentEx)document);
      fireMarkupModelCreated(project, newModel);
      if ((model = ConcurrencyUtil.cacheOrGet(markupModelMap, project, newModel)) == newModel) {
        documentMarkupModelManager.registerDocument(document);
      }
      else {
        newModel.dispose();
        fireMarkupModelDisposed(project, newModel);
      }
    }

    return model;
  }

  /**
   * Returns a list of markup models previously created for the specified document
   * by {@link #forDocument} with {@code create=true}. The result is identical to iterating
   * over all opened projects and calling {@link #forDocument} with {@code create=false}.
   */
  @ApiStatus.Experimental
  public static @Unmodifiable @NotNull List<? extends MarkupModel> getExistingMarkupModels(@NotNull Document document) {
    if (document instanceof DocumentWindow documentWindow) {
      Document delegate = documentWindow.getDelegate();
      List<? extends MarkupModel> baseMarkupModels = getExistingMarkupModels(delegate);
      return ContainerUtil.map(baseMarkupModels, model -> new MarkupModelWindow((MarkupModelEx)model, documentWindow));
    }
    ConcurrentMap<Project, MarkupModelImpl> markupModelMap = document.getUserData(MARKUP_MODEL_MAP_KEY);
    return markupModelMap != null ? List.copyOf(markupModelMap.values()) : Collections.emptyList();
  }

  private static @NotNull ConcurrentMap<Project, MarkupModelImpl> getMarkupModelMap(@NotNull Document document) {
    ConcurrentMap<Project, MarkupModelImpl> markupModelMap = document.getUserData(MARKUP_MODEL_MAP_KEY);
    if (markupModelMap == null) {
      ConcurrentMap<Project, MarkupModelImpl> newMap = new ConcurrentHashMap<>();
      markupModelMap = ((UserDataHolderEx)document).putUserDataIfAbsent(MARKUP_MODEL_MAP_KEY, newMap);
    }
    return markupModelMap;
  }

  static void removeMarkupModel(@NotNull Document document, @NotNull Project project) {
    MarkupModelImpl removed = getMarkupModelMap(document).remove(project);
    if (removed != null) {
      removed.dispose();
      fireMarkupModelDisposed(project, removed);
    }
  }

  private static void fireMarkupModelCreated(@Nullable Project project, @NotNull MarkupModelEx markupModel) {
    Application app = getApplication();
    if (app != null) {
      app.getMessageBus().syncPublisher(DocumentMarkupListener.TOPIC).markupModelCreated(project, markupModel);
    }
  }

  private static void fireMarkupModelDisposed(@Nullable Project project, @NotNull MarkupModelEx markupModel) {
    Application app = getApplication();
    if (app != null) {
      app.getMessageBus().syncPublisher(DocumentMarkupListener.TOPIC).markupModelDisposed(project, markupModel);
    }
  }

  private static @Nullable Application getApplication() {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      return app;
    }
    return null;
  }
}
