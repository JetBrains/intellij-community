package org.jetbrains.jewel.samples.ideplugin.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

internal class JewelWizardDialogAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = checkNotNull(event.project) { "Project not available" }
        val scope = project.service<ProjectScopeProviderService>().scope

        scope.launch(Dispatchers.EDT) {
            WizardDialogWrapper(
                    project = project,
                    title = "Jewel Demo wizard",
                    pages = listOf(FirstPage(project), SecondPage()),
                )
                .showAndGet()
        }
    }
}

private class FirstPage(private val project: Project) : WizardPage {
    override val canGoBackwards: StateFlow<Boolean> = MutableStateFlow(true)

    private val checkboxChecked = MutableStateFlow(false)
    override val canGoForward: StateFlow<Boolean> = checkboxChecked

    @Composable
    override fun PageContent() {
        Column {
            Text("This is the first page!", style = JewelTheme.typography.h1TextStyle)

            Spacer(Modifier.height(16.dp))

            Text("Project name: ${project.name}")

            Spacer(Modifier.height(16.dp))

            val checked by checkboxChecked.collectAsState()
            CheckboxRow("Allow going to next step", checked, { checkboxChecked.value = it })
        }
    }
}

private class SecondPage : WizardPage {
    override val canGoBackwards: StateFlow<Boolean> = MutableStateFlow(true)
    override val canGoForward: StateFlow<Boolean> = MutableStateFlow(true)

    @Composable
    override fun PageContent() {
        Column {
            Text("This is the second page!", style = JewelTheme.typography.h1TextStyle)

            Spacer(Modifier.height(16.dp))

            var count by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                launch {
                    while (true) {
                        delay(1.seconds.inWholeMilliseconds)
                        count++
                    }
                }
            }

            Text("You've been staring at this for $count second(s)")
        }
    }
}
