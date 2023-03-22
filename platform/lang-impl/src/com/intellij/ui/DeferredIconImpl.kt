// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.DeferredIconRepaintScheduler.RepaintRequest
import com.intellij.ui.icons.*
import com.intellij.ui.icons.RowIcon
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil.scale
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBScalableIcon
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Graphics
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiConsumer
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JTree

private const val MIN_AUTO_UPDATE_MILLIS = 950
private val ourRepaintScheduler = DeferredIconRepaintScheduler()
private val EMPTY_ICON: Icon = EmptyIcon.create(16).withIconPreScaled(false)
private val ourIconCalculatingExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("IconCalculating Pool", 1)

class DeferredIconImpl<T> : JBScalableIcon, DeferredIcon, RetrievableIcon, IconWithToolTip, CopyableIcon {
  companion object {
    fun <T> withoutReadAction(baseIcon: Icon?, param: T, evaluator: Function<in T, out Icon>): DeferredIcon {
      return DeferredIconImpl(baseIcon = baseIcon, param = param, needReadAction = false, evaluator = evaluator, listener = null)
    }

    fun equalIcons(icon1: Icon?, icon2: Icon?): Boolean {
      return if (icon1 is DeferredIconImpl<*> && icon2 is DeferredIconImpl<*>) {
        paramsEqual(icon1, icon2)
      }
      else icon1 == icon2
    }

    private fun paramsEqual(icon1: DeferredIconImpl<*>, icon2: DeferredIconImpl<*>): Boolean {
      return icon1.param == icon2.param && equalIcons(icon1.scaledDelegateIcon, icon2.scaledDelegateIcon)
    }
  }

  private val delegateIcon: Icon

  @Volatile
  private var scaledDelegateIcon: Icon
  private var myScaledIconCache: DeferredIconImpl<T>?
  private var evaluator: Function<in T, out Icon>?
  private var scheduledRepaints: MutableSet<RepaintRequest>? = null

  @Volatile
  private var myIsScheduled = false

  @JvmField
  val param: T

  val isNeedReadAction: Boolean
  var isDone = false
    private set
  private var lastCalcTime: Long = 0
  private var lastTimeSpent: Long = 0
  private var modificationCount = AtomicLong(0)
  private val evalListener: BiConsumer<in DeferredIcon?, in Icon>?

  private constructor(icon: DeferredIconImpl<T>) : super(icon) {
    delegateIcon = icon.delegateIcon
    scaledDelegateIcon = icon.delegateIcon
    myScaledIconCache = null
    evaluator = icon.evaluator
    myIsScheduled = icon.myIsScheduled
    param = icon.param
    isNeedReadAction = icon.isNeedReadAction
    isDone = icon.isDone
    lastCalcTime = icon.lastCalcTime
    lastTimeSpent = icon.lastTimeSpent
    evalListener = icon.evalListener
    modificationCount = icon.modificationCount
  }

  internal constructor(baseIcon: Icon?,
                       param: T,
                       needReadAction: Boolean,
                       evaluator: Function<in T, out Icon>,
                       listener: BiConsumer<in DeferredIcon?, in Icon>?) {
    this.param = param
    delegateIcon = baseIcon ?: EMPTY_ICON
    scaledDelegateIcon = delegateIcon
    myScaledIconCache = null
    this.evaluator = evaluator
    isNeedReadAction = needReadAction
    evalListener = listener
    checkDelegationDepth()
  }

  constructor(baseIcon: Icon?, param: T, needReadAction: Boolean, evaluator: com.intellij.util.Function<in T, out Icon>) :
    this(baseIcon = baseIcon,
         param = param,
         needReadAction = needReadAction,
         evaluator = Function<T, Icon> { t: T -> evaluator.`fun`(t) },
         listener = null)

  override fun getModificationCount(): Long = modificationCount.get()

  override fun replaceBy(replacer: IconReplacer): Icon = DeferredIconAfterReplace(original = this, replacer = replacer)

  override fun copy(): DeferredIconImpl<T> {
    return DeferredIconImpl(this)
  }

  override fun scale(scale: Float): DeferredIconImpl<T> {
    if (getScale() == scale) {
      return this
    }
    var icon = myScaledIconCache
    if (icon == null || icon.scale != scale) {
      icon = DeferredIconImpl(this)
      icon.setScale(ScaleType.OBJ_SCALE.of(scale))
      myScaledIconCache = icon
    }
    icon.scaledDelegateIcon = scale(icon.delegateIcon, null, scale)
    return icon
  }

  override fun getBaseIcon(): Icon {
    return delegateIcon
  }

  private fun checkDelegationDepth() {
    var depth = 0
    var each: DeferredIconImpl<*> = this
    while (each.scaledDelegateIcon is DeferredIconImpl<*> && depth < 50) {
      depth++
      each = each.scaledDelegateIcon as DeferredIconImpl<*>
    }
    if (depth >= 50) {
      logger<DeferredIconImpl<*>>().error("Too deep deferred icon nesting")
    }
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val scaledDelegateIcon = scaledDelegateIcon
    if (!(scaledDelegateIcon is DeferredIconImpl<*> &&
          scaledDelegateIcon.scaledDelegateIcon is DeferredIconImpl<*>)) {
      //SOE protection
      scaledDelegateIcon.paintIcon(c, g, x, y)
    }
    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y)
    }
  }

  override fun notifyPaint(c: Component, x: Int, y: Int) {
    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y)
    }
  }

  private fun needScheduleEvaluation(): Boolean {
    return !(isDone || PowerSaveMode.isEnabled())
  }

  @VisibleForTesting
  fun scheduleEvaluation(c: Component?, x: Int, y: Int): Future<*>? {
    // It is important to extract the repaint target here:
    // the component may be a temporary component used by some list or tree to paint elements
    val repaintRequest = ourRepaintScheduler.createRepaintRequest(c, x, y)
    AppUIUtil.invokeOnEdt {
      if (isDone) {
        return@invokeOnEdt
      }

      var scheduledRepaints = scheduledRepaints
      if (scheduledRepaints == null) {
        this.scheduledRepaints = mutableSetOf(repaintRequest)
      }
      else {
        if (!scheduledRepaints.contains(repaintRequest)) {
          if (scheduledRepaints.size == 1) {
            scheduledRepaints = HashSet(scheduledRepaints)
          }
          scheduledRepaints.add(repaintRequest)
        }
      }
    }
    if (myIsScheduled) {
      return null
    }
    myIsScheduled = true
    return ourIconCalculatingExecutor.submit {
      val oldWidth = scaledDelegateIcon.iconWidth
      val evaluated = arrayOfNulls<Icon>(1)
      var success = true
      if (isNeedReadAction) {
        success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority { IconDeferrerImpl.evaluateDeferred { evaluated[0] = evaluate() } }
      }
      else {
        IconDeferrerImpl.evaluateDeferred { evaluated[0] = evaluate() }
      }
      val result = evaluated[0]
      if (!success || result == null) {
        myIsScheduled = false
        EdtScheduledExecutorService.getInstance().schedule({
                                                             if (needScheduleEvaluation()) {
                                                               scheduleEvaluation(c, x, y)
                                                             }
                                                           }, MIN_AUTO_UPDATE_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        return@submit
      }
      scaledDelegateIcon = result
      modificationCount.incrementAndGet()
      checkDelegationDepth()
      processRepaints(oldWidth, result)
    }
  }

  private fun processRepaints(oldWidth: Int, result: Icon) {
    val shouldRevalidate = Registry.`is`("ide.tree.deferred.icon.invalidates.cache") && scaledDelegateIcon.iconWidth != oldWidth
    ApplicationManager.getApplication().invokeLater({
                                                      val repaints = scheduledRepaints
                                                      setDone(result)
                                                      if (equalIcons(result, delegateIcon)) {
                                                        return@invokeLater
                                                      }
                                                      for (repaintRequest in repaints!!) {
                                                        val actualTarget = repaintRequest.getActualTarget() ?: continue

                                                        // revalidate will not work: JTree caches size of nodes
                                                        if (shouldRevalidate && actualTarget is JTree) {
                                                          TreeUtil.invalidateCacheAndRepaint(actualTarget.ui)
                                                        }

                                                        //System.err.println("Repaint rectangle " + repaintRequest.getPaintingParentRec());
                                                        ourRepaintScheduler.scheduleRepaint(repaintRequest, iconWidth, iconHeight, false)
                                                      }
                                                    }, ModalityState.any())
  }

  private fun setDone(result: Icon) {
    evalListener?.accept(this, result)
    isDone = true
    evaluator = null
    scheduledRepaints = null
  }

  override fun retrieveIcon(): Icon {
    if (isDone) {
      return scaledDelegateIcon
    }
    return if (EDT.isCurrentThreadEdt()) {
      scaledDelegateIcon
    }
    else evaluate()
  }

  override fun evaluate(): Icon {
    // Icon evaluation is not something that should be related to any client
    val result = try {
      withClientId(null as ClientId?).use {
        evaluator?.apply(param) ?: EMPTY_ICON
      }
    }
    catch (e: IndexNotReadyException) {
      EMPTY_ICON
    }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      checkDoesntReferenceThis(result)
    }
    return if (scale != 1f && result is ScalableIcon) result.scale(scale) else result
  }

  private fun checkDoesntReferenceThis(icon: Icon?) {
    check(icon !== this) { "Loop in icons delegation" }
    when (icon) {
      is DeferredIconImpl<*> -> {
        checkDoesntReferenceThis(icon.scaledDelegateIcon)
      }
      is LayeredIcon -> {
        for (layer in icon.allLayers) {
          checkDoesntReferenceThis(layer)
        }
      }
      is RowIcon -> {
        val count: Int = icon.iconCount
        for (i in 0 until count) {
          checkDoesntReferenceThis(icon.getIcon(i))
        }
      }
    }
  }

  override fun getIconWidth(): Int {
    return scaledDelegateIcon.iconWidth
  }

  override fun getIconHeight(): Int {
    return scaledDelegateIcon.iconHeight
  }

  override fun getToolTip(composite: Boolean): String? {
    return if (scaledDelegateIcon is IconWithToolTip) {
      (scaledDelegateIcon as IconWithToolTip).getToolTip(composite)
    }
    else null
  }

  override fun equals(other: Any?): Boolean {
    return other is DeferredIconImpl<*> && paramsEqual(this, other)
  }

  override fun hashCode(): Int {
    return Objects.hash(param, scaledDelegateIcon)
  }

  override fun toString(): String {
    return "Deferred. Base=$scaledDelegateIcon"
  }

  /**
   * Later, it may be needed to implement more interfaces here. Ideally, the same as in the DeferredIconImpl itself.
   */
  private class DeferredIconAfterReplace<T>(private val original: DeferredIconImpl<T>,
                                            private val replacer: IconReplacer) : ReplaceableIcon, UpdatableIcon {
    private var originalEvaluatedIcon: Icon
    private var resultIcon: Icon

    init {
      originalEvaluatedIcon = original.scaledDelegateIcon
      resultIcon = replacer.replaceIcon(originalEvaluatedIcon)
    }

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      if (original.needScheduleEvaluation()) {
        original.scheduleEvaluation(c, x, y)
      }
      else if (originalEvaluatedIcon !== original.scaledDelegateIcon) {
        originalEvaluatedIcon = original.scaledDelegateIcon
        resultIcon = replacer.replaceIcon(originalEvaluatedIcon)
      }
      resultIcon.paintIcon(c, g, x, y)
    }

    override fun getIconWidth(): Int = resultIcon.iconWidth

    override fun getIconHeight(): Int = resultIcon.iconHeight

    override fun replaceBy(replacer: IconReplacer): Icon = replacer.replaceIcon(original)

    override fun getModificationCount(): Long = original.getModificationCount()

    override fun notifyPaint(c: Component, x: Int, y: Int) {
      original.notifyPaint(c, x, y)
    }
  }
}
