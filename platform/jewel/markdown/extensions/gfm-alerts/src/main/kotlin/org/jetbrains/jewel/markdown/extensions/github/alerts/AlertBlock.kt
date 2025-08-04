// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.commonmark.node.CustomBlock

internal sealed class AlertBlock : CustomBlock() {
    class Note : AlertBlock()

    class Tip : AlertBlock()

    class Important : AlertBlock()

    class Warning : AlertBlock()

    class Caution : AlertBlock()
}
