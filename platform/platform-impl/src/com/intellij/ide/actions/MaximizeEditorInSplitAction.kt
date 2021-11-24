// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DrawUtil
import com.intellij.util.SmartList
import com.intellij.util.animation.Easing.bezier
import com.intellij.util.animation.JBAnimator
import com.intellij.util.animation.animation
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nullable
import java.awt.Component
import java.util.function.DoubleConsumer

class MaximizeEditorInSplitAction : DumbAwareAction() {
  val myActiveAnimators = SmartList<JBAnimator>()
  init {
    templatePresentation.text = IdeBundle.message("action.maximize.editor") + "/" +IdeBundle.message("action.normalize.splits")
  }
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return
    myActiveAnimators.forEach { Disposer.dispose(it) }
    myActiveAnimators.clear()
    val splittersToMaximize = getSplittersToMaximize(e)
    if (splittersToMaximize.isNotEmpty()) {
      splittersToMaximize.forEach {
        setProportion(project, it.first, if (it.second) it.first.maximumProportion else it.first.minimumProportion)
      }
    }
    else {
      val splittersToNormalize = getSplittersToNormalize(e)
      if (splittersToNormalize.isNotEmpty()) {
        splittersToNormalize.forEach { setProportion(project, it, .5f) }
      }
    }
  }

  fun setProportion(disposable : Disposable, splitter: Splitter, value: Float) {
    if (!Registry.`is`("ide.experimental.ui.animations") || DrawUtil.isSimplifiedUI()) {
      splitter.proportion = value
      return
    }
    val animator = JBAnimator(disposable).also { myActiveAnimators.add(it) }
    val from = splitter.proportion.toDouble()
    val to = value.toDouble()
    animator.animate(
      animation(from, to, DoubleConsumer { v -> splitter.proportion = v.toFloat() })
        .setDuration(350)
        .setEasing(bezier(0.215, 0.61, 0.355, 1.0))
        .runWhenExpired {
          Disposer.dispose(animator)
          myActiveAnimators.remove(animator)
        }
    )
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation

    presentation.isEnabled = true

    val splittersToMaximize = getSplittersToMaximize(event)
    if (splittersToMaximize.isNotEmpty()) {
      presentation.text = IdeBundle.message("action.maximize.editor")
      presentation.putClientProperty(CURRENT_STATE_IS_MAXIMIZED_KEY, false)
      return
    }

    val splittersToNormalize = getSplittersToNormalize(event)
    if (splittersToNormalize.isNotEmpty()) {
      presentation.text = IdeBundle.message("action.normalize.splits")
      presentation.putClientProperty(CURRENT_STATE_IS_MAXIMIZED_KEY, true)
      return
    }
    presentation.isEnabled = false
  }

  fun getSplittersToMaximize(e: AnActionEvent): Set<Pair<Splitter, Boolean>> {
    val project = e.project
    val editor = e.getData(CommonDataKeys.HOST_EDITOR)
    if (project == null || editor == null) {
      return emptySet()
    }
    return getSplittersToMaximize(project, editor)
  }

  companion object {
    val CURRENT_STATE_IS_MAXIMIZED_KEY = Key.create<Boolean>("CURRENT_STATE_IS_MAXIMIZED")
    fun getSplittersToMaximize(project: @Nullable Project, editor: @Nullable Editor): Set<Pair<Splitter, Boolean>> {
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return emptySet()
      val set = HashSet<Pair<Splitter, Boolean>>()
      var comp = editor.component as Component?
      while (comp != editorManager.mainSplitters && comp != null) {
        val parent = comp.parent
        if (parent is Splitter && UIUtil.isClientPropertyTrue(parent, EditorsSplitters.SPLITTER_KEY)) {
          if (parent.firstComponent == comp) {
            if (parent.proportion < parent.maximumProportion - .005) {
              set.add(Pair(parent, true))
            }
          }
          else {
            if (parent.proportion > parent.minimumProportion + .005) {
              set.add(Pair(parent, false))
            }
          }
        }
        comp = parent
      }
      return set
    }

    fun getSplittersToNormalize(e: AnActionEvent): Set<Splitter> {
      val project = e.project
      val editor = e.getData(CommonDataKeys.HOST_EDITOR)
      if (project == null || editor == null /*|| !e.isRelatedToSplits()*/) {
        return emptySet()
      }
      val set = HashSet<Splitter>()
      var splitters = ComponentUtil.getParentOfType(EditorsSplitters::class.java, editor.component as Component)
      while (splitters != null) {
        val candidate = ComponentUtil.getParentOfType(EditorsSplitters::class.java, splitters.parent)
        splitters = candidate ?: break
      }
      if (splitters != null) {
        val splitterList = UIUtil.findComponentsOfType(splitters, Splitter::class.java)
        splitterList.removeIf { !UIUtil.isClientPropertyTrue(it, EditorsSplitters.SPLITTER_KEY) }
        set.addAll(splitterList)
      }
      return set
    }

    fun isThereSplitter(e: AnActionEvent): Boolean {
      val project = e.project
      val editor = e.getData(CommonDataKeys.HOST_EDITOR)
      if (project == null || editor == null) return false
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return false
      var comp = editor.component as Component
      while (comp != editorManager.mainSplitters) {
        val parent = comp.parent
        if (parent is Splitter) {
          return true
        }
        comp = parent
      }
      return false
    }
  }
}