// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Key;

import javax.swing.*;
import java.util.function.Function;

public interface PresentationIconUpdater {

  Key<PresentationIconUpdater> UPDATER_KEY = Key.create("PresentationUpdaterKey");
  PresentationIconUpdater DEFAULT = new DefaultPresentationIconUpdater();

  void performUpdate(Presentation presentation, Function<Icon, Icon> iconTransformer);

  default PresentationIconUpdater andThen(PresentationIconUpdater another) {
    return (presentation, iconTransformer) -> {
      this.performUpdate(presentation, iconTransformer);
      another.performUpdate(presentation, iconTransformer);
    };
  }

  default void registerToPresentation(Presentation presentation) {
    presentation.putClientProperty(UPDATER_KEY, this);
  }

  static void updateIcons(Presentation presentation, Function<Icon, Icon> iconTransformer) {
    PresentationIconUpdater updater = presentation.getClientProperty(UPDATER_KEY);
    if (updater == null) updater = DEFAULT;

    updater.performUpdate(presentation, iconTransformer);
  }
}
