package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

/** Icon keys for the decorated window control buttons (minimize, maximize, restore, close). */
public object DecoratedWindowIconKeys {
    /** The icon key for the minimize button. */
    public val minimize: IconKey = PathIconKey("window/minimize.svg", this::class.java)

    /** The icon key for the maximize button. */
    public val maximize: IconKey = PathIconKey("window/maximize.svg", this::class.java)

    /** The icon key for the restore button. */
    public val restore: IconKey = PathIconKey("window/restore.svg", this::class.java)

    /** The icon key for the close button. */
    public val close: IconKey = PathIconKey("window/close.svg", this::class.java)
}
