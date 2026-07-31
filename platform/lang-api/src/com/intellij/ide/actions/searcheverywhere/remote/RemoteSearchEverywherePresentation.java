// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote;

import javax.swing.ListCellRenderer;

/**
 * @deprecated The old Search Everywhere API is being sunset. This functionality is obsolete.
 */
@Deprecated
public interface RemoteSearchEverywherePresentation {

  ListCellRenderer<? extends RemoteSearchEverywherePresentation> getPresentationRenderer();
}
