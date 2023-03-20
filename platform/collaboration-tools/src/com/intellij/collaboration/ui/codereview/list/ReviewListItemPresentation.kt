// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

interface ReviewListItemPresentation {

  val title: @NlsSafe String
  val id: @NlsSafe String
  val createdDate: Date
  val author: UserPresentation?
  val tagGroup: NamedCollection<TagPresentation>?
  val mergeableStatus: Status?
  val buildStatus: Status?
  val state: @NlsSafe String?
  val userGroup1: NamedCollection<UserPresentation>?
  val userGroup2: NamedCollection<UserPresentation>?
  val commentsCounter: CommentsCounter?

  data class Simple(override val title: String,
                    override val id: String,
                    override val createdDate: Date,
                    override val author: UserPresentation? = null,
                    override val tagGroup: NamedCollection<TagPresentation>? = null,
                    override val mergeableStatus: Status? = null,
                    override val buildStatus: Status? = null,
                    override val state: String? = null,
                    override val userGroup1: NamedCollection<UserPresentation>? = null,
                    override val userGroup2: NamedCollection<UserPresentation>? = null,
                    override val commentsCounter: CommentsCounter? = null) : ReviewListItemPresentation

  data class Status(val icon: Icon, val tooltip: @Nls String)

  data class CommentsCounter(val count: Int, val tooltip: @Nls String)
}
