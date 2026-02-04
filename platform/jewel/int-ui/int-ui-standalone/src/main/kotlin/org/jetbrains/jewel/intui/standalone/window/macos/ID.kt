package org.jetbrains.jewel.intui.standalone.window.macos

import com.sun.jna.NativeLong
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/** Could be an address in memory (if pointer to a class or method) or a value (like 0 or 1) */
@ApiStatus.Internal
@InternalJewelApi
@Suppress("OVERRIDE_DEPRECATION") // Copied code
public class ID : NativeLong {
    public constructor()

    public constructor(peer: Long) : super(peer)

    public fun booleanValue(): Boolean = toInt() != 0

    override fun toByte(): Byte = toInt().toByte()

    override fun toChar(): Char = toInt().toChar()

    override fun toShort(): Short = toInt().toShort()

    @Suppress("RedundantOverride") // Without this, we get a SOE
    override fun toInt(): Int = super.toInt()

    public companion object {
        @JvmField public val NIL: ID = ID(0L)
    }
}
