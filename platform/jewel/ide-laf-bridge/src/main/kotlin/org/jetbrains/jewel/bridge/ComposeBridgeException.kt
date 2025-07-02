package org.jetbrains.jewel.bridge

public typealias JewelBridgeException = ComposeBridgeException

public sealed class ComposeBridgeException(override val message: String?) : RuntimeException(message) {
    public class KeyNotFoundException(key: String, type: String) :
        ComposeBridgeException("Key '$key' not found in Swing LaF, was expecting a value of type $type")

    public class KeysNotFoundException(keys: List<String>, type: String) :
        ComposeBridgeException(
            "Keys ${keys.joinToString(", ") { "'$it'" }} not found in Swing LaF, " +
                "was expecting a value of type $type"
        )
}

@Suppress("NOTHING_TO_INLINE") // Same implementation as error()
internal inline fun keyNotFound(key: String, type: String): Nothing =
    throw ComposeBridgeException.KeyNotFoundException(key, type)

@Suppress("NOTHING_TO_INLINE") // Same implementation as error()
internal inline fun keysNotFound(keys: List<String>, type: String): Nothing =
    throw ComposeBridgeException.KeysNotFoundException(keys, type)
