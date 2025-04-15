// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import fleet.util.text.isTrueBasedOnRange

/**
 * Utilities to detect Hiragana and Katakana character in a multiplatform setting.
 *
 * Regenerate arrays with this JVM code:
 * ```
 * fun main() {
 *     val ideographicList = mutableListOf<Int>()
 *     val katakanaList = mutableListOf<Int>()
 *     val hiraganaList = mutableListOf<Int>()
 *     val commonList = mutableListOf<Int>()
 *
 * 	  (Character.MIN_CODE_POINT..Character.MAX_CODE_POINT).forEach {
 *       val ideographic = Character.isIdeographic(it)
 *       val katakana = Character.UnicodeScript.of(it).let { it == Character.UnicodeScript.KATAKANA }
 *       val hiragana = Character.UnicodeScript.of(it).let { it == Character.UnicodeScript.HIRAGANA }
 *  	   val common= Character.UnicodeScript.of(it).let { it == Character.UnicodeScript.COMMON }
 *
 *       if (ideographicList.size % 2 == 0 == ideographic) ideographicList.add(it)
 *       if (katakanaList.size % 2 == 0 == katakana) katakanaList.add(it)
 *       if (hiraganaList.size % 2 == 0 == hiragana) hiraganaList.add(it)
 *       if (commonList.size % 2 == 0 == common) commonList.add(it)
 *     }
 *
 *     println(ideographicList)
 *     println(katakanaList)
 *     println(hiraganaList)
 *     println(commonList)
 * }
 * ```
 */

private val ideographic = intArrayOf(12294, 12296, 12321, 12330, 12344, 12347, 13312, 19904, 19968, 40957, 63744, 64110, 64112, 64218, 94180, 94181, 94208, 100344, 100352, 101590, 101632, 101641, 110960, 111356, 131072, 173790, 173824, 177973, 177984, 178206, 178208, 183970, 183984, 191457, 194560, 195102, 196608, 201547)
private val katakana = intArrayOf(12449, 12539, 12541, 12544, 12784, 12800, 13008, 13055, 13056, 13144, 65382, 65392, 65393, 65438, 110592, 110593, 110948, 110952)
private val hiragana = intArrayOf(12353, 12439, 12445, 12448, 110593, 110879, 110928, 110931, 127488, 127489)
private val common = intArrayOf(0, 65, 91, 97, 123, 170, 171, 186, 187, 192, 215, 216, 247, 248, 697, 736, 741, 746, 748, 768, 884, 885, 894, 895, 901, 902, 903, 904, 1541, 1542, 1548, 1549, 1563, 1564, 1567, 1568, 1600, 1601, 1757, 1758, 2274, 2275, 2404, 2406, 3647, 3648, 4053, 4057, 4347, 4348, 5867, 5870, 5941, 5943, 6146, 6148, 6149, 6150, 7379, 7380, 7393, 7394, 7401, 7405, 7406, 7412, 7413, 7416, 7418, 7419, 8192, 8204, 8206, 8293, 8294, 8305, 8308, 8319, 8320, 8335, 8352, 8384, 8448, 8486, 8487, 8490, 8492, 8498, 8499, 8526, 8527, 8544, 8585, 8588, 8592, 9255, 9280, 9291, 9312, 10240, 10496, 11124, 11126, 11158, 11159, 11264, 11776, 11859, 12272, 12284, 12288, 12293, 12294, 12295, 12296, 12321, 12336, 12344, 12348, 12352, 12443, 12445, 12448, 12449, 12539, 12541, 12688, 12704, 12736, 12772, 12832, 12896, 12927, 13008, 13055, 13056, 13144, 13312, 19904, 19968, 42752, 42786, 42888, 42891, 43056, 43066, 43310, 43311, 43471, 43472, 43867, 43868, 43882, 43884, 64830, 64832, 65040, 65050, 65072, 65107, 65108, 65127, 65128, 65132, 65279, 65280, 65281, 65313, 65339, 65345, 65371, 65382, 65392, 65393, 65438, 65440, 65504, 65511, 65512, 65519, 65529, 65534, 65792, 65795, 65799, 65844, 65847, 65856, 65936, 65949, 66000, 66045, 66273, 66300, 94178, 94180, 113824, 113828, 118784, 119030, 119040, 119079, 119081, 119143, 119146, 119163, 119171, 119173, 119180, 119210, 119214, 119273, 119520, 119540, 119552, 119639, 119648, 119673, 119808, 119893, 119894, 119965, 119966, 119968, 119970, 119971, 119973, 119975, 119977, 119981, 119982, 119994, 119995, 119996, 119997, 120004, 120005, 120070, 120071, 120075, 120077, 120085, 120086, 120093, 120094, 120122, 120123, 120127, 120128, 120133, 120134, 120135, 120138, 120145, 120146, 120486, 120488, 120780, 120782, 120832, 126065, 126133, 126209, 126270, 126976, 127020, 127024, 127124, 127136, 127151, 127153, 127168, 127169, 127184, 127185, 127222, 127232, 127406, 127462, 127488, 127489, 127491, 127504, 127548, 127552, 127561, 127568, 127570, 127584, 127590, 127744, 128728, 128736, 128749, 128752, 128765, 128768, 128884, 128896, 128985, 128992, 129004, 129024, 129036, 129040, 129096, 129104, 129114, 129120, 129160, 129168, 129198, 129200, 129202, 129280, 129401, 129402, 129484, 129485, 129620, 129632, 129646, 129648, 129653, 129656, 129659, 129664, 129671, 129680, 129705, 129712, 129719, 129728, 129731, 129744, 129751, 129792, 129939, 129940, 129995, 130032, 130042, 917505, 917506, 917536, 917632)

// Receiver to reduce the visibility, public for testing purposes
fun NameUtilCore.isIdeographic(codepoint: Int): Boolean = isTrueBasedOnRange(codepoint, ideographic)
fun NameUtilCore.isKatakana(codepoint: Int): Boolean = isTrueBasedOnRange(codepoint, katakana)
fun NameUtilCore.isHiragana(codepoint: Int): Boolean = isTrueBasedOnRange(codepoint, hiragana)
fun NameUtilCore.isCommonScript(codepoints: Int): Boolean = isTrueBasedOnRange(codepoints, common)
