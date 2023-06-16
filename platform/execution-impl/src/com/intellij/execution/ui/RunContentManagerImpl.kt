// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.execution.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.KillableProcess
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.ui.layout.impl.DockableGridContainerFactory
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.ui.content.*
import com.intellij.ui.content.Content.CLOSE_LISTENER_KEY
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.docking.DockManager
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.SmartList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.KeyboardFocusManager
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Predicate
import javax.swing.Icon

private val EXECUTOR_KEY: Key<Executor> = Key.create("Executor")

@Suppress("LiftReturnOrAssignment")
class RunContentManagerImpl(private val project: Project) : RunContentManager {
  private val toolWindowIdToBaseIcon: MutableMap<String, Icon> = HashMap()
  private val toolWindowIdZBuffer = ConcurrentLinkedDeque<String>()

  init {
    val containerFactory = DockableGridContainerFactory()
    DockManager.getInstance(project).register(DockableGridContainerFactory.TYPE, containerFactory, project)
    AppUIExecutor.onUiThread().expireWith(project).submit { init() }
  }

  companion object {
    @JvmField
    val ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY = Key.create<Boolean>("ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY")

    @ApiStatus.Internal
    @JvmField
    val TEMPORARY_CONFIGURATION_KEY = Key.create<RunnerAndConfigurationSettings>("TemporaryConfiguration")

    @JvmStatic
    fun copyContentAndBehavior(descriptor: RunContentDescriptor, contentToReuse: RunContentDescriptor?) {
      if (contentToReuse != null) {
        val attachedContent = contentToReuse.attachedContent
        if (attachedContent != null && attachedContent.isValid) {
          descriptor.setAttachedContent(attachedContent)
        }
        if (contentToReuse.isReuseToolWindowActivation) {
          descriptor.isActivateToolWindowWhenAdded = contentToReuse.isActivateToolWindowWhenAdded
        }
        if (descriptor.processHandler?.getUserData(RunContentDescriptor.CONTENT_TOOL_WINDOW_ID_KEY) == null) {
          descriptor.contentToolWindowId = contentToReuse.contentToolWindowId
        }
        descriptor.isSelectContentWhenAdded = contentToReuse.isSelectContentWhenAdded
      }
    }

    @JvmStatic
    fun isTerminated(content: Content): Boolean {
      val processHandler = getRunContentDescriptorByContent(content)?.processHandler ?: return true
      return processHandler.isProcessTerminated
    }

    @JvmStatic
    fun getRunContentDescriptorByContent(content: Content): RunContentDescriptor? {
      return content.getUserData(RunContentDescriptor.DESCRIPTOR_KEY)
    }

    @JvmStatic
    fun getExecutorByContent(content: Content): Executor? = content.getUserData(EXECUTOR_KEY)

    @JvmStatic
    fun getLiveIndicator(icon: Icon?): Icon = when (ExperimentalUI.isNewUI()) {
      true -> IconManager.getInstance().withIconBadge(icon ?: EmptyIcon.ICON_13, JBUI.CurrentTheme.IconBadge.SUCCESS)
      else -> ExecutionUtil.getLiveIndicator(icon)
    }
  }

  // must be called on EDT
  private fun init() {
    val messageBusConnection = project.messageBus.connect()
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        toolWindowIdZBuffer.retainAll(toolWindowManager.toolWindowIdSet)
        val activeToolWindowId = toolWindowManager.activeToolWindowId
        if (activeToolWindowId != null && toolWindowIdZBuffer.remove(activeToolWindowId)) {
          toolWindowIdZBuffer.addFirst(activeToolWindowId)
        }
      }
    })

    messageBusConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        processToolWindowContentManagers { _, contentManager ->
          val contents = contentManager.contents
          for (content in contents) {
            val runContentDescriptor = getRunContentDescriptorByContent(content) ?: continue
            if (runContentDescriptor.processHandler?.isProcessTerminated == true) {
              contentManager.removeContent(content, true)
            }
          }
        }
      }
    })
  }

  @ApiStatus.Internal
  fun registerToolWindow(executor: Executor): ContentManager {
    val toolWindowManager = getToolWindowManager()
    val toolWindowId = executor.toolWindowId
    var toolWindow = toolWindowManager.getToolWindow(toolWindowId)
    if (toolWindow != null) {
      return toolWindow.contentManager
    }

    toolWindow = toolWindowManager.registerToolWindow(RegisterToolWindowTask(
      id = toolWindowId,
      icon = executor.toolWindowIcon,
      stripeTitle = executor::getActionName
    ))
    toolWindow.setToHideOnEmptyContent(true)
    if (DefaultRunExecutor.EXECUTOR_ID == executor.id || Registry.`is`("debugger.new.tool.window.layout.dnd", false)) {
      toolWindow.component.putClientProperty(ToolWindowContentUi.ALLOW_DND_FOR_TABS, true)
    }
    val contentManager = toolWindow.contentManager
    contentManager.addDataProvider(object : DataProvider {
      override fun getData(dataId: String): Any? {
        if (PlatformCoreDataKeys.HELP_ID.`is`(dataId)) {
          return executor.helpId
        }
        return null
      }
    })
    initToolWindow(executor, toolWindowId, executor.toolWindowIcon, contentManager)
    return contentManager
  }

  private fun initToolWindow(executor: Executor?, toolWindowId: String, toolWindowIcon: Icon, contentManager: ContentManager) {
    toolWindowIdToBaseIcon.put(toolWindowId, toolWindowIcon)
    contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        if (event.operation != ContentManagerEvent.ContentOperation.add) {
          return
        }

        val content = event.content
        // Content manager contains contents related with different executors.
        // Try to get executor from content.
        // Must contain this user data since all content is added by this class.
        val contentExecutor = executor ?: getExecutorByContent(content)!!
        syncPublisher.contentSelected(getRunContentDescriptorByContent(content), contentExecutor)
        content.helpId = contentExecutor.helpId
      }
    })
    Disposer.register(contentManager, Disposable {
      contentManager.removeAllContents(true)
      toolWindowIdZBuffer.remove(toolWindowId)
      toolWindowIdToBaseIcon.remove(toolWindowId)
    })
    toolWindowIdZBuffer.addLast(toolWindowId)
  }

  private val syncPublisher: RunContentWithExecutorListener
    get() = project.messageBus.syncPublisher(RunContentManager.TOPIC)

  override fun toFrontRunContent(requestor: Executor, handler: ProcessHandler) {
    val descriptor = getDescriptorBy(handler, requestor) ?: return
    toFrontRunContent(requestor, descriptor)
  }

  override fun toFrontRunContent(requestor: Executor, descriptor: RunContentDescriptor) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val contentManager = getContentManagerForRunner(requestor, descriptor)
      val content = getRunContentByDescriptor(contentManager, descriptor)
      if (content != null) {
        contentManager.setSelectedContent(content)
        getToolWindowManager().getToolWindow(getToolWindowIdForRunner(requestor, descriptor))!!.show(null)
      }
    }, project.disposed)
  }

  override fun hideRunContent(executor: Executor, descriptor: RunContentDescriptor) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val toolWindow = getToolWindowManager().getToolWindow(getToolWindowIdForRunner(executor, descriptor))
      toolWindow?.hide(null)
    }, project.disposed)
  }

  override fun getSelectedContent(): RunContentDescriptor? {
    for (activeWindow in toolWindowIdZBuffer) {
      val contentManager = getContentManagerByToolWindowId(activeWindow) ?: continue
      val selectedContent = contentManager.selectedContent
                            ?: if (contentManager.contentCount == 0) {
                              // continue to the next window if the content manager is empty
                              continue
                            }
                            else {
                              // stop iteration over windows because there is some content in the window and the window is the last used one
                              break
                            }
      // here we have selected content
      return getRunContentDescriptorByContent(selectedContent)
    }
    return null
  }

  override fun removeRunContent(executor: Executor, descriptor: RunContentDescriptor): Boolean {
    val contentManager = getContentManagerForRunner(executor, descriptor)
    val content = getRunContentByDescriptor(contentManager, descriptor)
    return content != null && contentManager.removeContent(content, true)
  }

  override fun showRunContent(executor: Executor, descriptor: RunContentDescriptor) {
    showRunContent(executor, descriptor, descriptor.executionId)
  }

  private fun showRunContent(executor: Executor, descriptor: RunContentDescriptor, executionId: Long) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val contentManager = getContentManagerForRunner(executor, descriptor)
    val toolWindowId = getToolWindowIdForRunner(executor, descriptor)
    val oldDescriptor = chooseReuseContentForDescriptor(contentManager, descriptor, executionId, descriptor.displayName, getReuseCondition(toolWindowId))
    val content: Content?
    if (oldDescriptor == null) {
      content = createNewContent(descriptor, executor)
    }
    else {
      content = oldDescriptor.attachedContent!!
      SingleContentSupplier.removeSubContentsOfContent(content, rightNow = true)
      syncPublisher.contentRemoved(oldDescriptor, executor)
      Disposer.dispose(oldDescriptor) // is of the same category, can be reused
    }
    content.executionId = executionId
    content.component = descriptor.component
    content.setPreferredFocusedComponent(descriptor.preferredFocusComputable)
    content.putUserData(RunContentDescriptor.DESCRIPTOR_KEY, descriptor)
    content.putUserData(EXECUTOR_KEY, executor)
    content.displayName = descriptor.displayName
    descriptor.setAttachedContent(content)
    val toolWindow = getToolWindowManager().getToolWindow(toolWindowId)
    val processHandler = descriptor.processHandler
    if (processHandler != null) {
      val processAdapter = object : ProcessAdapter() {
        override fun startNotified(event: ProcessEvent) {
          UIUtil.invokeLaterIfNeeded {
            content.icon = getLiveIndicator(descriptor.icon)
            var icon = toolWindowIdToBaseIcon[toolWindowId]
            if (ExperimentalUI.isNewUI() && icon is ScalableIcon) {
              icon = loadIconCustomVersionOrScale(icon = icon, size = 20)
            }
            toolWindow!!.setIcon(getLiveIndicator(icon))
          }
        }

        override fun processTerminated(event: ProcessEvent) {
          AppUIUtil.invokeLaterIfProjectAlive(project) {
            val manager = getContentManagerByToolWindowId(toolWindowId) ?: return@invokeLaterIfProjectAlive
            val alive = isAlive(manager)
            setToolWindowIcon(alive, toolWindow!!)
            val icon = descriptor.icon
            content.icon = if (icon == null) executor.disabledIcon else IconLoader.getTransparentIcon(icon)
          }
        }
      }
      processHandler.addProcessListener(processAdapter)
      val disposer = content.disposer
      if (disposer != null) {
        Disposer.register(disposer, Disposable { processHandler.removeProcessListener(processAdapter) })
      }
    }

    addRunnerContentListener(descriptor)

    if (oldDescriptor == null) {
      contentManager.addContent(content)
      content.putUserData(CLOSE_LISTENER_KEY, CloseListener(content, executor))
    }
    if (descriptor.isSelectContentWhenAdded /* also update selection when reused content is already selected  */
        || oldDescriptor != null && content.manager!!.isSelected(content)) {
      content.manager!!.setSelectedContent(content)
    }

    if (!descriptor.isActivateToolWindowWhenAdded) {
      return
    }

    ApplicationManager.getApplication().invokeLater(Runnable {
      // let's activate tool window, but don't move focus
      //
      // window.show() isn't valid here, because it will not
      // mark the window as "last activated" windows and thus
      // some action like navigation up/down in stacktrace wont
      // work correctly
      var focus = descriptor.isAutoFocusContent
      if (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner == null) {
        // This is to cover the case, when the focus was in Run tool window already,
        // and it was reset due to us replacing tool window content.
        // We're restoring focus in the tool window in this case.
        // It shouldn't harm in any case - having no focused component isn't useful at all.
        focus = true
      }
      getToolWindowManager().getToolWindow(toolWindowId)!!.activate(descriptor.activationCallback, focus, focus)
    }, project.disposed)
  }

  private fun getContentManagerByToolWindowId(toolWindowId: String): ContentManager? {
    project.serviceIfCreated<RunDashboardManager>()?.let {
      if (it.toolWindowId == toolWindowId) {
        return if (toolWindowIdToBaseIcon.contains(toolWindowId)) it.dashboardContentManager else null
      }
    }
    return getToolWindowManager().getToolWindow(toolWindowId)?.contentManagerIfCreated
  }

  private fun addRunnerContentListener(descriptor: RunContentDescriptor) {
    val runContentManager = descriptor.runnerLayoutUi?.contentManager
    val mainContent = descriptor.attachedContent
    if (runContentManager != null && mainContent != null) {
      runContentManager.addContentManagerListener(object : ContentManagerListener {
        // remove the toolwindow tab that is moved outside via drag and drop
        // if corresponding run/debug tab was hidden in debugger layout settings
        override fun contentRemoved(event: ContentManagerEvent) {
          val toolWindowContentManager = InternalDecoratorImpl.findTopLevelDecorator(mainContent.component)?.contentManager ?: return
          val allContents = if (toolWindowContentManager is ContentManagerImpl)
            toolWindowContentManager.contentsRecursively else toolWindowContentManager.contents.toList()
          val removedContent = event.content
          val movedContent = allContents.find { it.displayName == removedContent.displayName }
          if (movedContent != null) {
            movedContent.manager?.removeContent(movedContent, false)
          }
        }
      })
    }
  }

  override fun getReuseContent(executionEnvironment: ExecutionEnvironment): RunContentDescriptor? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return null
    }

    val contentToReuse = executionEnvironment.contentToReuse
    if (contentToReuse != null) {
      return contentToReuse
    }

    val toolWindowId = getContentDescriptorToolWindowId(executionEnvironment)
    val reuseCondition: Predicate<Content>?
    val contentManager: ContentManager
    if (toolWindowId == null) {
      contentManager = getContentManagerForRunner(executionEnvironment.executor, null)
      reuseCondition = null
    }
    else {
      contentManager = getOrCreateContentManagerForToolWindow(toolWindowId, executionEnvironment.executor)
      reuseCondition = getReuseCondition(toolWindowId)
    }
    return chooseReuseContentForDescriptor(contentManager, null, executionEnvironment.executionId, executionEnvironment.toString(), reuseCondition)
  }

  private fun getReuseCondition(toolWindowId: String): Predicate<Content>? {
    val runDashboardManager = RunDashboardManager.getInstance(project)
    return if (runDashboardManager.toolWindowId == toolWindowId) runDashboardManager.reuseCondition else null
  }

  override fun findContentDescriptor(requestor: Executor, handler: ProcessHandler): RunContentDescriptor? {
    return getDescriptorBy(handler, requestor)
  }

  override fun showRunContent(info: Executor, descriptor: RunContentDescriptor, contentToReuse: RunContentDescriptor?) {
    copyContentAndBehavior(descriptor, contentToReuse)
    showRunContent(info, descriptor, descriptor.executionId)
  }

  private fun getContentManagerForRunner(executor: Executor, descriptor: RunContentDescriptor?): ContentManager {
    return descriptor?.attachedContent?.manager ?: getOrCreateContentManagerForToolWindow(getToolWindowIdForRunner(executor, descriptor), executor)
  }

  private fun getOrCreateContentManagerForToolWindow(id: String, executor: Executor): ContentManager {
    val contentManager = getContentManagerByToolWindowId(id)
    if (contentManager != null) {
      updateToolWindowDecoration(id, executor)
      return contentManager
    }

    val dashboardManager = RunDashboardManager.getInstance(project)
    if (dashboardManager.toolWindowId == id) {
      initToolWindow(null, dashboardManager.toolWindowId, dashboardManager.toolWindowIcon, dashboardManager.dashboardContentManager)
      return dashboardManager.dashboardContentManager
    }
    else {
      return registerToolWindow(executor)
    }
  }

  override fun getToolWindowByDescriptor(descriptor: RunContentDescriptor): ToolWindow? {
    descriptor.contentToolWindowId?.let {
      return getToolWindowManager().getToolWindow(it)
    }
    processToolWindowContentManagers { toolWindow, contentManager ->
      if (getRunContentByDescriptor(contentManager, descriptor) != null) {
        return toolWindow
      }
    }
    return null
  }

  private fun updateToolWindowDecoration(id: String, executor: Executor) {
    if (project.serviceIfCreated<RunDashboardManager>()?.toolWindowId == id) {
      return
    }

    getToolWindowManager().getToolWindow(id)?.apply {
      stripeTitle = executor.actionName
      setIcon(executor.toolWindowIcon)
      toolWindowIdToBaseIcon[id] = executor.toolWindowIcon
    }
  }

  private inline fun processToolWindowContentManagers(processor: (ToolWindow, ContentManager) -> Unit) {
    val toolWindowManager = getToolWindowManager()
    val processedToolWindowIds = HashSet<String>()
    for (executor in Executor.EXECUTOR_EXTENSION_NAME.extensionList) {
      val toolWindowId = executor.toolWindowId
      if (processedToolWindowIds.add(toolWindowId)) {
        val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: continue
        processor(toolWindow, toolWindow.contentManagerIfCreated ?: continue)
      }
    }

    project.serviceIfCreated<RunDashboardManager>()?.let {
      val toolWindowId = it.toolWindowId
      if (toolWindowIdToBaseIcon.contains(toolWindowId)) {
        processor(toolWindowManager.getToolWindow(toolWindowId) ?: return, it.dashboardContentManager)
      }
    }
  }

  override fun getAllDescriptors(): List<RunContentDescriptor> {
    val descriptors: MutableList<RunContentDescriptor> = SmartList()
    processToolWindowContentManagers { _, contentManager ->
      for (content in contentManager.contents) {
        getRunContentDescriptorByContent(content)?.let {
          descriptors.add(it)
        }
      }
    }
    return descriptors
  }

  override fun selectRunContent(descriptor: RunContentDescriptor) {
    processToolWindowContentManagers { _, contentManager ->
      val content = getRunContentByDescriptor(contentManager, descriptor) ?: return@processToolWindowContentManagers
      contentManager.setSelectedContent(content)
      return
    }
  }

  private fun getToolWindowManager() = ToolWindowManager.getInstance(project)

  override fun getContentDescriptorToolWindowId(configuration: RunConfiguration?): String? {
    if (configuration != null) {
      val runDashboardManager = RunDashboardManager.getInstance(project)
      if (runDashboardManager.isShowInDashboard(configuration)) {
        return runDashboardManager.toolWindowId
      }
    }
    return null
  }

  override fun getToolWindowIdByEnvironment(executionEnvironment: ExecutionEnvironment): String {
    // Also there are some places where ToolWindowId.RUN or ToolWindowId.DEBUG are used directly.
    // For example, HotSwapProgressImpl.NOTIFICATION_GROUP. All notifications for this group is shown in Debug tool window,
    // however such notifications should be shown in Run Dashboard tool window, if run content is redirected to Run Dashboard tool window.
    val toolWindowId = getContentDescriptorToolWindowId(executionEnvironment)
    return toolWindowId ?: executionEnvironment.executor.toolWindowId
  }

  private fun getDescriptorBy(handler: ProcessHandler, runnerInfo: Executor): RunContentDescriptor? {
    fun find(manager: ContentManager?): RunContentDescriptor? {
      if (manager == null) return null
      val contents =
      if (manager is ContentManagerImpl) {
        manager.contentsRecursively
      } else {
        manager.contents.toList()
      }
      for (content in contents) {
        val runContentDescriptor = getRunContentDescriptorByContent(content)
        if (runContentDescriptor?.processHandler === handler) {
          return runContentDescriptor
        }
      }
      return null
    }

    find(getContentManagerForRunner(runnerInfo, null))?.let {
      return it
    }
    find(getContentManagerByToolWindowId(project.serviceIfCreated<RunDashboardManager>()?.toolWindowId ?: return null) ?: return null)?.let {
      return it
    }
    return null
  }

  fun moveContent(executor: Executor, descriptor: RunContentDescriptor) {
    val content = descriptor.attachedContent ?: return
    val oldContentManager = content.manager
    val newContentManager = getOrCreateContentManagerForToolWindow(getToolWindowIdForRunner(executor, descriptor), executor)
    if (oldContentManager == null || oldContentManager === newContentManager) return
    val listener = content.getUserData(CLOSE_LISTENER_KEY)
    if (listener != null) {
      oldContentManager.removeContentManagerListener(listener)
    }
    oldContentManager.removeContent(content, false)
    if (isAlive(descriptor)) {
      if (!isAlive(oldContentManager)) {
        updateToolWindowIcon(oldContentManager, false)
      }
      if (!isAlive(newContentManager)) {
        updateToolWindowIcon(newContentManager, true)
      }
    }
    newContentManager.addContent(content)
    // Close listener is added to new content manager by propertyChangeListener in BaseContentCloseListener.
  }

  private fun updateToolWindowIcon(contentManagerToUpdate: ContentManager, alive: Boolean) {
    processToolWindowContentManagers { toolWindow, contentManager ->
      if (contentManagerToUpdate == contentManager) {
        setToolWindowIcon(alive, toolWindow)
        return
      }
    }
  }

  private fun setToolWindowIcon(alive: Boolean, toolWindow: ToolWindow) {
    val base = toolWindowIdToBaseIcon.get(toolWindow.id)
    toolWindow.setIcon(if (alive) getLiveIndicator(base) else base ?: EmptyIcon.ICON_13)
  }

  private inner class CloseListener(content: Content, private val myExecutor: Executor) : BaseContentCloseListener(content, project) {
    override fun disposeContent(content: Content) {
      try {
        val descriptor = getRunContentDescriptorByContent(content)
        syncPublisher.contentRemoved(descriptor, myExecutor)
        if (descriptor != null) {
          Disposer.dispose(descriptor)
        }
      }
      finally {
        content.release()
      }
    }

    override fun closeQuery(content: Content, projectClosing: Boolean): Boolean {
      val descriptor = getRunContentDescriptorByContent(content) ?: return true
      if (Content.TEMPORARY_REMOVED_KEY.get(content, false)) return true
      val processHandler = descriptor.processHandler
      if (processHandler == null || processHandler.isProcessTerminated) {
        return true
      }

      val sessionName = descriptor.displayName
      val killable = processHandler is KillableProcess && (processHandler as KillableProcess).canKillProcess()
      val task = object : WaitForProcessTask(processHandler, sessionName, projectClosing, project) {
        override fun onCancel() {
          if (killable && !processHandler.isProcessTerminated) {
            (processHandler as KillableProcess).killProcess()
          }
        }
      }
      if (killable) {
        val cancelText = ExecutionBundle.message("terminating.process.progress.kill")
        task.cancelText = cancelText
        task.cancelTooltipText = cancelText
      }
      return askUserAndWait(processHandler, sessionName, task)
    }
  }
}

private fun chooseReuseContentForDescriptor(contentManager: ContentManager,
                                            descriptor: RunContentDescriptor?,
                                            executionId: Long,
                                            preferredName: String?,
                                            reuseCondition: Predicate<in Content>?): RunContentDescriptor? {
  var content: Content? = null
  if (descriptor != null) {
    //Stage one: some specific descriptors (like AnalyzeStacktrace) cannot be reused at all
    if (descriptor.isContentReuseProhibited) {
      return null
    }
    // stage two: try to get content from descriptor itself
    val attachedContent = descriptor.attachedContent
    if (attachedContent != null && attachedContent.isValid
        && (descriptor.displayName == attachedContent.displayName || !attachedContent.isPinned)) {
      val contents =
      if (contentManager is ContentManagerImpl) {
        contentManager.contentsRecursively
      } else {
        contentManager.contents.toList()
      }
      if (contents.contains(attachedContent)) {
        content = attachedContent
      }
    }
  }

  // stage three: choose the content with name we prefer
  if (content == null) {
    content = getContentFromManager(contentManager, preferredName, executionId, reuseCondition)
  }
  if (content == null || !RunContentManagerImpl.isTerminated(content) || content.executionId == executionId && executionId != 0L) {
    return null
  }

  val oldDescriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content) ?: return null
  if (oldDescriptor.isContentReuseProhibited) {
    return null
  }
  if (descriptor == null || oldDescriptor.reusePolicy.canBeReusedBy(descriptor)) {
    return oldDescriptor
  }
  return null
}

private fun getContentFromManager(contentManager: ContentManager,
                                  preferredName: String?,
                                  executionId: Long,
                                  reuseCondition: Predicate<in Content>?): Content? {
  val contents =
    if (contentManager is ContentManagerImpl) {
      contentManager.contentsRecursively
    } else {
      contentManager.contents.toMutableList()
    }
  val first = contentManager.selectedContent
  if (first != null && contents.remove(first)) {
    //selected content should be checked first
    contents.add(0, first)
  }
  if (preferredName != null) {
    // try to match content with specified preferred name
    for (c in contents) {
      if (canReuseContent(c, executionId) && preferredName == c.displayName) {
        return c
      }
    }
  }

  // return first "good" content
  return contents.firstOrNull {
    canReuseContent(it, executionId) && (reuseCondition == null || reuseCondition.test(it))
  }
}

private fun canReuseContent(c: Content, executionId: Long): Boolean {
  return !c.isPinned && RunContentManagerImpl.isTerminated(c) && !(c.executionId == executionId && executionId != 0L)
}

private fun getToolWindowIdForRunner(executor: Executor, descriptor: RunContentDescriptor?): String {
  return descriptor?.contentToolWindowId ?: executor.toolWindowId
}

private fun createNewContent(descriptor: RunContentDescriptor, executor: Executor): Content {
  val content = ContentFactory.getInstance().createContent(descriptor.component, descriptor.displayName, true)
  content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
  if (AdvancedSettings.getBoolean("start.run.configurations.pinned")) content.isPinned = true
  content.icon = descriptor.icon ?: executor.toolWindowIcon
  return content
}

private fun getRunContentByDescriptor(contentManager: ContentManager, descriptor: RunContentDescriptor): Content? {
  return contentManager.contents.firstOrNull {
    descriptor == RunContentManagerImpl.getRunContentDescriptorByContent(it)
  }
}

private fun isAlive(contentManager: ContentManager): Boolean {
  return contentManager.contents.any {
    val descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(it)
    descriptor != null && isAlive(descriptor)
  }
}

private fun isAlive(descriptor: RunContentDescriptor): Boolean {
  val handler = descriptor.processHandler
  return handler != null && !handler.isProcessTerminated
}