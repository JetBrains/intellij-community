package org.jetbrains.jewel.bridge

/** A sealed exception hierarchy for errors that occur when a required key is not found in the Swing LaF. */
public sealed class JewelBridgeException(override val message: String?) : RuntimeException(message) {
    /** Thrown when a single expected Swing LaF key is not found. */
    public class KeyNotFoundException(key: String, type: String) :
        JewelBridgeException("Key '$key' not found in Swing LaF, was expecting a value of type $type")

    /** Thrown when none of a set of expected Swing LaF keys are found. */
    public class KeysNotFoundException(keys: List<String>, type: String) :
        JewelBridgeException(
            "Keys ${keys.joinToString(", ") { "'$it'" }} not found in Swing LaF, " +
                "was expecting a value of type $type"
        )
}

@Suppress("NOTHING_TO_INLINE") // Same implementation as error()
internal inline fun keyNotFound(key: String, type: String): Nothing =
    throw JewelBridgeException.KeyNotFoundException(key, type)

@Suppress("NOTHING_TO_INLINE") // Same implementation as error()
internal inline fun keysNotFound(keys: List<String>, type: String): Nothing =
    throw JewelBridgeException.KeysNotFoundException(keys, type)
