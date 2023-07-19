package org.jetbrains.jewel

import java.io.InputStream

interface SvgPatcher {

    fun patchSvg(rawSvg: InputStream): String
}
