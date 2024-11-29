package org.jetbrains.jewel.window.utils.macos

import com.sun.jna.NativeLong

/** Could be an address in memory (if pointer to a class or method) or a value (like 0 or 1) */
@Suppress("OVERRIDE_DEPRECATION") // Copied code
internal class ID : NativeLong {
    constructor()

    constructor(peer: Long) : super(peer)

    fun booleanValue(): Boolean = toInt() != 0

    override fun toByte(): Byte = toInt().toByte()

    override fun toChar(): Char = toInt().toChar()

    override fun toShort(): Short = toInt().toShort()

    @Suppress("RedundantOverride") // Without this, we get a SOE
    override fun toInt(): Int = super.toInt()

    companion object {
        @JvmField val NIL = ID(0L)
    }
}
