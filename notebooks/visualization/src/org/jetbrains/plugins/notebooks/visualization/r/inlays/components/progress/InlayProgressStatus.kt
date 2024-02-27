package org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress

import org.jetbrains.annotations.Nls

data class InlayProgressStatus(val progress: ProgressStatus, @Nls val statusText: String = "")