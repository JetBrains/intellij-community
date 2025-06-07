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

    public fun createHighlighter(): CodeHighlighter = LexerBasedCodeHighlighter(project, reHighlightingRequests)

    public companion object {
        public fun getInstance(project: Project): CodeHighlighterFactory = project.service()
    }
}
