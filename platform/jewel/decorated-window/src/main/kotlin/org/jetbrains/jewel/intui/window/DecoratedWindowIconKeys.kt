package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

public object DecoratedWindowIconKeys {
    public val minimize: IconKey = PathIconKey("window/minimize.svg", this::class.java)
    public val maximize: IconKey = PathIconKey("window/maximize.svg", this::class.java)
    public val restore: IconKey = PathIconKey("window/restore.svg", this::class.java)
    public val close: IconKey = PathIconKey("window/close.svg", this::class.java)
}
