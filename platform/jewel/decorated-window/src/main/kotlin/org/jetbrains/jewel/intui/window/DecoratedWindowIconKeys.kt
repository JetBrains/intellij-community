package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

public object DecoratedWindowIconKeys {
    public val minimize: IconKey = PathIconKey("icons/intui/window/minimize.svg", this::class.java)
    public val maximize: IconKey = PathIconKey("icons/intui/window/maximize.svg", this::class.java)
    public val restore: IconKey = PathIconKey("icons/intui/window/restore.svg", this::class.java)
    public val close: IconKey = PathIconKey("icons/intui/window/close.svg", this::class.java)
}
