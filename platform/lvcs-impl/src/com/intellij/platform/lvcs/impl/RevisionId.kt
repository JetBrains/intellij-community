// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

sealed class RevisionId {
  data object Current : RevisionId()
  data class ChangeSet(val id: Long) : RevisionId()
}

val ChangeSetActivityItem.revisionId: RevisionId get() = RevisionId.ChangeSet(id)