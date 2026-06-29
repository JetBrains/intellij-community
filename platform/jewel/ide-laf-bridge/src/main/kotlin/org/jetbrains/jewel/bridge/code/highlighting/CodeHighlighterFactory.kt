package org.jetbrains.jewel.bridge.code.highlighting

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter

/**
 * A project-level IDE service that creates [CodeHighlighter] instances backed by the IDE's editor color scheme, and
 * triggers re-highlighting when the color scheme changes.
 */
@Service(Service.Level.PROJECT)
public class CodeHighlighterFactory(private val project: Project, private val coroutineScope: CoroutineScope) {
    private val reHighlightingRequests = MutableSharedFlow<Unit>(replay = 0)

    init {
        project.messageBus
            .connect(coroutineScope)
            .subscribe(
                EditorColorsManager.TOPIC,
                EditorColorsListener { coroutineScope.launch { reHighlightingRequests.emit(Unit) } },
            )
    }

    /** Creates a new [CodeHighlighter] backed by the IDE's current editor color scheme for this project. */
    public fun createHighlighter(): CodeHighlighter = IntelliJCodeHighlighter(project, reHighlightingRequests)

    /** Provides the [getInstance] factory for obtaining the [CodeHighlighterFactory] service for a given project. */
    public companion object {
        /** Returns the [CodeHighlighterFactory] service instance for the given [project]. */
        public fun getInstance(project: Project): CodeHighlighterFactory = project.service()
    }
}
