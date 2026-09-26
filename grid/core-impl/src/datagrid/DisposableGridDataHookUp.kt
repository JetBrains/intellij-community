package com.intellij.database.datagrid

import com.intellij.openapi.Disposable

/**
 * A hookup whose lifetime belongs to whoever stores it, rather than to a grid or a file editor.
 *
 * It lets a holder of long-lived sources name a single type covering hookups that are otherwise unrelated.
 */
interface DisposableGridDataHookUp : GridDataHookUp<GridRow, GridColumn>, Disposable
