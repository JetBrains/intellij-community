// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote;

import javax.swing.*;

public interface RemoteSearchEverywherePresentation {

  ListCellRenderer<? extends RemoteSearchEverywherePresentation> getPresentationRenderer();
}
