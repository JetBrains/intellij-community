package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Service(Service.Level.PROJECT)
internal class ReleasesSampleService : CoroutineScope, Disposable {
    private val dispatcher = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

    override val coroutineContext = SupervisorJob() + CoroutineName("ReleasesSampleService") + dispatcher

    private val originalContentSource = MutableStateFlow<ContentSource<*>>(AndroidStudioReleases)

    private val _filter = MutableStateFlow("")
    val filter = _filter.asStateFlow()

    private val filteredContent = MutableStateFlow(originalContentSource.value)
    val content = filteredContent.asStateFlow()

    init {
        combine(originalContentSource, filter) { source, filter ->
                val normalizedFilter = filter.trim()
                if (normalizedFilter.isBlank()) return@combine source

                val filteredContentItems = source.items.filter { it.matches(normalizedFilter) }

                FilteredContentSource(filteredContentItems, source)
            }
            .onEach { filteredContent.emit(it) }
            .launchIn(this)
    }

    fun setContentSource(contentSource: ContentSource<*>) {
        if (contentSource != originalContentSource.value) {
            originalContentSource.tryEmit(contentSource)
            resetFilter()
        }
    }

    fun resetFilter() {
        filterContent("")
    }

    fun filterContent(filter: String) {
        _filter.tryEmit(filter)
    }

    override fun dispose() {
        cancel("Disposing ${this::class.simpleName}...")
        coroutineContext.cancel(CancellationException("Shutting down project..."))
    }
}
