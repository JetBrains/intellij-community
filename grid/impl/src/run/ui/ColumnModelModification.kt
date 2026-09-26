package com.intellij.database.run.ui

import com.intellij.openapi.util.ModificationTracker

interface ColumnModelModification {
  fun getColumnModelModificationTracker(): ModificationTracker
}