package fleet.codepoints

/**
 * Binary search over a sorted array of ranges stored as triplets.
 *
 * The [ranges] array stores triplets: [start, end, value] where ranges are sorted by start.
 * Returns the value from the triplet if the key falls within a range, otherwise returns [defaultValue].
 *
 * @param key The key to look up
 * @param ranges Array of triplets [start, end, value] sorted by start
 * @param defaultValue Value to return if key is not in any range
 * @return The value for the matching range, or defaultValue
 */
internal fun binarySearchRange(key: Int, ranges: IntArray, defaultValue: Int): Int {
    var low = 0
    var high = ranges.size / 3 - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val idx = mid * 3
        val start = ranges[idx]
        val end = ranges[idx + 1]
        when {
            key < start -> high = mid - 1
            key > end -> low = mid + 1
            else -> return ranges[idx + 2]
        }
    }
    return defaultValue
}

internal fun isAscii(codepoint: Int): Boolean = codepoint < 0x80

/**
 * Fast ASCII lowercase conversion.
 * Converts A-Z to a-z, leaves other characters unchanged.
 */
internal fun asciiToLowerCase(codepoint: Int): Int =
    if ((codepoint - 'A'.code).toUInt() <= 25u) codepoint + 32 else codepoint

/**
 * Fast ASCII uppercase conversion.
 * Converts a-z to A-Z, leaves other characters unchanged.
 */
internal fun asciiToUpperCase(codepoint: Int): Int =
    if ((codepoint - 'a'.code).toUInt() <= 25u) codepoint - 32 else codepoint
