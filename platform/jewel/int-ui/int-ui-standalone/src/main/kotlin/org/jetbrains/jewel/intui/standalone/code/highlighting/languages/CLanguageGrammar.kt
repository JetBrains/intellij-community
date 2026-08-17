// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns ported from plugins/textmate/lib/bundles/cpp/syntaxes/c.tmLanguage.json.
//
// The function-call rule gains a leading (?<![A-Za-z0-9_]), which is ours. The bundle excludes keywords
// with a lookahead but anchors the name to nothing, so against `catch (x)` the flat rule fails at `c` and
// matches `atch (` one character right.
//
// Differences you can see:
//  - Escapes and printf placeholders inside a string stay string-colored. Both rules can only fire where
//    the string rule has already claimed the text, so ported flat they would only ever hit a `%d` written
//    outside a string, which is a modulo.
//  - `p->count` colors `count` but not `p`, and a field name away from a `.` or `->` stays plain.
//  - A `#if 0` block greys out down to its first `#endif` only; the bundle tracks nesting.
//  - The `0x` prefix and the `f` and `UL` suffixes are part of the number. The bundle scopes them as
//    keywords, which needs a second pass over the match.
//  - Parameter names in a definition stay plain. The bundle knows it is inside a function head; the guess
//    it makes there is unanchored without that.
//  - Operators are unstyled by default. IntelliJ maps keyword.operator to DEFAULT_OPERATION_SIGN, which
//    neither default scheme colors, so they only show up if a theme defines that key.

// #storage_types, storage.type.built-in.primitive.c
private const val PRIMITIVES =
    "(?-mix:(?<!\\w)(?:unsigned|signed|double|_Bool|short|float|long|void|char|bool|int)(?!\\w))"

// #storage_types, storage.type.built-in.c
private const val BUILT_IN_TYPES =
    "(?-mix:(?<!\\w)(?:atomic_uint_least64_t|atomic_uint_least16_t|atomic_uint_least32_t|" +
        "pthread_rwlockattr_t|atomic_uint_fast64_t|atomic_uint_fast32_t|atomic_uint_fast16_t|" +
        "atomic_int_least64_t|atomic_int_least32_t|atomic_int_least16_t|atomic_uint_least8_t|" +
        "atomic_uint_fast8_t|atomic_int_least8_t|atomic_int_fast16_t|pthread_mutexattr_t|" +
        "atomic_int_fast32_t|atomic_int_fast64_t|atomic_int_fast8_t|pthread_condattr_t|atomic_ptrdiff_t|" +
        "pthread_rwlock_t|atomic_uintptr_t|atomic_uintmax_t|atomic_intmax_t|atomic_intptr_t|" +
        "atomic_char32_t|atomic_char16_t|pthread_mutex_t|pthread_cond_t|atomic_wchar_t|uint_least64_t|" +
        "uint_least32_t|uint_least16_t|pthread_once_t|pthread_attr_t|int_least32_t|pthread_key_t|" +
        "int_least16_t|int_least64_t|uint_least8_t|uint_fast16_t|uint_fast32_t|uint_fast64_t|" +
        "atomic_ushort|atomic_ullong|atomic_size_t|int_fast16_t|int_fast64_t|uint_fast8_t|atomic_short|" +
        "atomic_uchar|atomic_schar|int_least8_t|memory_order|atomic_llong|atomic_ulong|int_fast32_t|" +
        "atomic_long|atomic_uint|atomic_char|int_fast8_t|suseconds_t|atomic_bool|atomic_int|_Imaginary|" +
        "useconds_t|in_port_t|uintmax_t|uintmax_t|pthread_t|blksize_t|in_addr_t|uintptr_t|blkcnt_t|" +
        "uint16_t|uint32_t|uint64_t|u_quad_t|_Complex|intptr_t|intmax_t|intmax_t|segsz_t|u_short|nlink_t|" +
        "uint8_t|int64_t|int32_t|int16_t|fixpt_t|daddr_t|caddr_t|qaddr_t|ssize_t|clock_t|swblk_t|u_long|" +
        "mode_t|int8_t|time_t|ushort|u_char|quad_t|size_t|pid_t|gid_t|uid_t|dev_t|div_t|off_t|u_int|" +
        "key_t|ino_t|uint|id_t|id_t)(?!\\w))"

// #member_access, variable.other.member.c on group 5. The bundle's exclusion list keeps a type name from
// reading as a field.
private const val MEMBER_ACCESS =
    "((?:[a-zA-Z_]\\w*|(?<=\\]|\\)))\\s*)(?:((?:\\.\\*|\\.))|((?:->\\*|->)))((?:[a-zA-Z_]\\w*\\s*" +
        "(?:(?:(?:\\.\\*|\\.))|(?:(?:->\\*|->)))\\s*)*)\\s*(\\b(?!(?:atomic_uint_least64_t|" +
        "atomic_uint_least16_t|atomic_uint_least32_t|atomic_uint_least8_t|atomic_int_least16_t|" +
        "atomic_uint_fast64_t|atomic_uint_fast32_t|atomic_int_least64_t|atomic_int_least32_t|" +
        "pthread_rwlockattr_t|atomic_uint_fast16_t|pthread_mutexattr_t|atomic_int_fast16_t|" +
        "atomic_uint_fast8_t|atomic_int_fast64_t|atomic_int_least8_t|atomic_int_fast32_t|" +
        "atomic_int_fast8_t|pthread_condattr_t|atomic_uintptr_t|atomic_ptrdiff_t|pthread_rwlock_t|" +
        "atomic_uintmax_t|pthread_mutex_t|atomic_intmax_t|atomic_intptr_t|atomic_char32_t|" +
        "atomic_char16_t|pthread_attr_t|atomic_wchar_t|uint_least64_t|uint_least32_t|uint_least16_t|" +
        "pthread_cond_t|pthread_once_t|uint_fast64_t|uint_fast16_t|atomic_size_t|uint_least8_t|" +
        "int_least64_t|int_least32_t|int_least16_t|pthread_key_t|atomic_ullong|atomic_ushort|" +
        "uint_fast32_t|atomic_schar|atomic_short|uint_fast8_t|int_fast64_t|int_fast32_t|int_fast16_t|" +
        "atomic_ulong|atomic_llong|int_least8_t|atomic_uchar|memory_order|suseconds_t|int_fast8_t|" +
        "atomic_bool|atomic_char|atomic_uint|atomic_long|atomic_int|useconds_t|_Imaginary|blksize_t|" +
        "pthread_t|in_addr_t|uintptr_t|in_port_t|uintmax_t|uintmax_t|blkcnt_t|uint16_t|unsigned|" +
        "_Complex|uint32_t|intptr_t|intmax_t|intmax_t|uint64_t|u_quad_t|int64_t|int32_t|ssize_t|caddr_t|" +
        "clock_t|uint8_t|u_short|swblk_t|segsz_t|int16_t|fixpt_t|daddr_t|nlink_t|qaddr_t|size_t|time_t|" +
        "mode_t|signed|quad_t|ushort|u_long|u_char|double|int8_t|ino_t|uid_t|pid_t|_Bool|float|dev_t|" +
        "div_t|short|gid_t|off_t|u_int|key_t|id_t|uint|long|void|char|bool|id_t|int)\\b)" +
        "[a-zA-Z_]\\w*\\b(?!\\())"

// #function-call-innards, entity.name.function.c. The (?<![A-Za-z0-9_]) on the name branch is ours.
private const val FUNCTION_CALL =
    "(?x)\n(?!(?:while|for|do|if|else|switch|catch|enumerate|return|typeid|alignof|alignas|sizeof|" +
        "[cr]?iterate|and|and_eq|bitand|bitor|compl|not|not_eq|or|or_eq|typeid|xor|xor_eq|alignof|" +
        "alignas)\\s*\\()\n(\n(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*+|::)++  # actual name\n|\n" +
        "(?:(?<=operator)(?:[-*&<>=+!]+|\\(\\)|\\[\\]))\n)\n\\s*(\\()"

// #predefined_macros, support.constant.other.c
private const val PREDEFINED_MACROS =
    "\\b(__cplusplus|__DATE__|__FILE__|__LINE__|__STDC__|__STDC_HOSTED__|__STDC_NO_COMPLEX__|" +
        "__STDC_VERSION__|__STDCPP_THREADS__|__TIME__|NDEBUG|__OBJC__|__ASSEMBLER__|__ATOM__|__AVX__|" +
        "__AVX2__|_CHAR_UNSIGNED|__CLR_VER|_CONTROL_FLOW_GUARD|__COUNTER__|__cplusplus_cli|" +
        "__cplusplus_winrt|_CPPRTTI|_CPPUNWIND|_DEBUG|_DLL|__FUNCDNAME__|__FUNCSIG__|__FUNCTION__|" +
        "_INTEGRAL_MAX_BITS|__INTELLISENSE__|_ISO_VOLATILE|_KERNEL_MODE|_M_AMD64|_M_ARM|_M_ARM_ARMV7VE|" +
        "_M_ARM_FP|_M_ARM64|_M_CEE|_M_CEE_PURE|_M_CEE_SAFE|_M_FP_EXCEPT|_M_FP_FAST|_M_FP_PRECISE|" +
        "_M_FP_STRICT|_M_IX86|_M_IX86_FP|_M_X64|_MANAGED|_MSC_BUILD|_MSC_EXTENSIONS|_MSC_FULL_VER|" +
        "_MSC_VER|_MSVC_LANG|__MSVC_RUNTIME_CHECKS|_MT|_NATIVE_WCHAR_T_DEFINED|_OPENMP|_PREFAST|" +
        "__TIMESTAMP__|_VC_NO_DEFAULTLIB|_WCHAR_T_DEFINED|_WIN32|_WIN64|_WINRT_DLL|_ATL_VER|_MFC_VER|" +
        "__GFORTRAN__|__GNUC__|__GNUC_MINOR__|__GNUC_PATCHLEVEL__|__GNUG__|__STRICT_ANSI__|" +
        "__BASE_FILE__|__INCLUDE_LEVEL__|__ELF__|__VERSION__|__OPTIMIZE__|__OPTIMIZE_SIZE__|" +
        "__NO_INLINE__|__GNUC_STDC_INLINE__|__CHAR_UNSIGNED__|__WCHAR_UNSIGNED__|__REGISTER_PREFIX__|" +
        "__SIZE_TYPE__|__PTRDIFF_TYPE__|__WCHAR_TYPE__|__WINT_TYPE__|__INTMAX_TYPE__|__UINTMAX_TYPE__|" +
        "__SIG_ATOMIC_TYPE__|__INT8_TYPE__|__INT16_TYPE__|__INT32_TYPE__|__INT64_TYPE__|__UINT8_TYPE__|" +
        "__UINT16_TYPE__|__UINT32_TYPE__|__UINT64_TYPE__|__CHAR_BIT__|__SCHAR_MAX__|__WCHAR_MAX__|" +
        "__SHRT_MAX__|__INT_MAX__|__LONG_MAX__|__LONG_LONG_MAX__|__WINT_MAX__|__SIZE_MAX__|" +
        "__PTRDIFF_MAX__|__INTMAX_MAX__|__UINTMAX_MAX__|__SIG_ATOMIC_MAX__|__INTPTR_MAX__|" +
        "__UINTPTR_MAX__|__WCHAR_MIN__|__WINT_MIN__|__SIG_ATOMIC_MIN__|__SIZEOF_INT__|__SIZEOF_LONG__|" +
        "__SIZEOF_LONG_LONG__|__SIZEOF_SHORT__|__SIZEOF_POINTER__|__SIZEOF_FLOAT__|__SIZEOF_DOUBLE__|" +
        "__SIZEOF_LONG_DOUBLE__|__SIZEOF_SIZE_T__|__SIZEOF_WCHAR_T__|__SIZEOF_WINT_T__|" +
        "__SIZEOF_PTRDIFF_T__|__BYTE_ORDER__|__ORDER_LITTLE_ENDIAN__|__ORDER_BIG_ENDIAN__|" +
        "__ORDER_PDP_ENDIAN__|__FLOAT_WORD_ORDER__|__DEPRECATED|__EXCEPTIONS|__GXX_RTTI|" +
        "__USING_SJLJ_EXCEPTIONS__|__GXX_EXPERIMENTAL_CXX0X__|__GXX_WEAK__|__NEXT_RUNTIME__|__LP64__|" +
        "_LP64|__SSP__|__SSP_ALL__|__SSP_STRONG__|__SSP_EXPLICIT__|__SANITIZE_ADDRESS__|" +
        "__SANITIZE_THREAD__|__HAVE_SPECULATION_SAFE_VALUE|__GCC_HAVE_DWARF2_CFI_ASM|__FP_FAST_FMA|" +
        "__FP_FAST_FMAF|__FP_FAST_FMAL|__GCC_IEC_559|__GCC_IEC_559_COMPLEX|__NO_MATH_ERRNO__|" +
        "__has_builtin|__has_feature|__has_extension|__has_cpp_attribute|__has_c_attribute|" +
        "__has_attribute|__has_declspec_attribute|__is_identifier|__has_include|__has_include_next|" +
        "__has_warning|__FILE_NAME__|__clang__|__clang_major__|__clang_minor__|__clang_patchlevel__|" +
        "__clang_version__|__fp16|_Float16)\\b"

internal val C =
    LanguageGrammar(
        name = "c",
        aliases = listOf("cats", "h", "h.in", "i", "idc"),
        rules =
            listOf(
                // #comments — comment.block.c then comment.line.double-slash.c, both fused
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                TokenRule.comment("//[^\\r\\n]*"),
                // #preprocessor-rule-disabled — the body of a `#if 0` becomes
                // comment.block.preprocessor.if-branch.c. Fused begin to end, so nesting is not tracked.
                TokenRule.comment(
                    "(?ms)^[\\t ]*+(#)\\s*if\\b(?=\\s*\\(*\\b0+\\b\\)*\\s*(?:$|//|/\\*)).*?^[\\t ]*+#\\s*endif\\b"
                ),
                // #strings — string.quoted.double.c and string.quoted.single.c
                TokenRule.string("\"(?:\\\\.|[^\"\\\\])*\""),
                TokenRule.string("'(?:\\\\.|[^'\\\\])*'"),
                // #line_continuation_character — constant.character.escape.line-continuation.c
                TokenRule.constant("\\\\(?=\\n)"),
                // The preprocessor directives, each the begin of its own meta.preprocessor block. The
                // include path is folded in as group 3, since the bundle only reaches it from inside.
                TokenRule(
                    "(?m)^\\s*((#)\\s*(?:include(?:_next)?|import))\\b[\\t ]*(<[^>\\r\\n]*>)?",
                    mapOf(1 to TokenType.KEYWORD, 3 to TokenType.STRING),
                ),
                // entity.name.function.preprocessor.c on the macro name
                TokenRule(
                    "((?:(?:(?>\\s+)|(\\/\\*)((?>(?:[^\\*]|(?>\\*+)[^\\/])*)((?>\\*+)\\/)))+?|" +
                        "(?:(?:(?:(?:\\b|(?<=\\W))|(?=\\W))|\\A)|\\Z)))((#)\\s*define\\b)\\s+" +
                        "((?<!\\w)[a-zA-Z_]\\w*(?!\\w))(?:(\\()([^()\\\\]+)(\\)))?",
                    mapOf(5 to TokenType.KEYWORD, 7 to TokenType.FUNCTION_CALL),
                ),
                TokenRule.keyword("(?m)^\\s*(?:#)\\s*(?:if|ifdef|ifndef|elif|else|endif)\\b"),
                TokenRule.keyword("(?m)^\\s*(?:#)\\s*(?:error|warning)\\b"),
                TokenRule.keyword("(?m)^\\s*(?:#)\\s*(?:line|undef|pragma)\\b"),
                // #predefined_macros — support.constant.other.c
                TokenRule.constant(PREDEFINED_MACROS),
                // #switch_statement, #case_statement, #default_statement — keyword.control.*.c
                TokenRule.keyword("(?<!\\w)switch(?!\\w)"),
                TokenRule.keyword("(?<!\\w)case(?!\\w)"),
                TokenRule.keyword("(?<!\\w)default(?!\\w)"),
                // keyword.control.c
                TokenRule.keyword("\\b(break|continue|do|else|for|goto|if|_Pragma|return|while)\\b"),
                // #storage_types — the aggregate and asm words read as keywords; see the header
                TokenRule.keyword("(?-mix:\\b(enum|struct|union)\\b)"),
                TokenRule.keyword("\\b(?:__asm__|asm)\\b"),
                // keyword.other.typedef.c, then storage.modifier.c. The \b pair is ours: the bundle's rule is
                // the bare word, so `int mytypedefName` colors the substring, there and here.
                TokenRule.keyword("\\btypedef\\b"),
                TokenRule.keyword("\\b(const|extern|register|restrict|static|volatile|inline)\\b"),
                // #storage_types — storage.type.built-in.*. IntelliJ maps the whole storage.type family to
                // its keyword key, which is why `int` reads as a keyword here rather than as a type.
                TokenRule.keyword(PRIMITIVES),
                TokenRule.keyword(BUILT_IN_TYPES),
                // constant.other.variable.mac-classic.c, then the two variable.other.readwrite.*.c rules
                TokenRule.constant("\\bk[A-Z]\\w*\\b"),
                TokenRule.builtin("\\bg[A-Z]\\w*\\b"),
                TokenRule.builtin("\\bs[A-Z]\\w*\\b"),
                // constant.language.c
                TokenRule.constant("\\b(NULL|true|false|TRUE|FALSE)\\b"),
                // support.constant.mac-classic.c
                TokenRule.constant("\\b(noErr|kNilOptions|kInvalidID|kVariableLengthArray)\\b"),
                // #member_access — variable.other.member.c is group 5; group 1 is the object, left plain.
                // #block_innards runs this and the call rule ahead of $self, so both have to stay ahead of
                // the operators: in `argv[i]->len` the member match starts at the `-` and would lose the tie.
                TokenRule(MEMBER_ACCESS, mapOf(5 to TokenType.BUILTIN)),
                // #function-call-innards and #function-innards share this pattern, so it covers both a
                // call and the name in a definition
                TokenRule.functionCall(FUNCTION_CALL),
                // #operators — keyword.operator.*.c, in the bundle's order so `<<=` beats `<<` beats `<`
                TokenRule.operator("(?<![\\w$])(sizeof)(?![\\w$])"),
                TokenRule.operator("--"),
                TokenRule.operator("\\+\\+"),
                TokenRule.operator("%=|\\+=|-=|\\*=|(?<!\\()/="),
                TokenRule.operator("&=|\\^=|<<=|>>=|\\|="),
                TokenRule.operator("<<|>>"),
                TokenRule.operator("!=|<=|>=|==|<|>"),
                TokenRule.operator("&&|!|\\|\\|"),
                TokenRule.operator("&|\\||\\^|~"),
                TokenRule.operator("="),
                TokenRule.operator("%|\\*|/|-|\\+"),
                TokenRule.operator("\\?"),
                // support.type.* — the sys, pthread, stdint and mac-classic tables, then any name in _t.
                // IntelliJ maps support.type to its predefined-symbol key, which is our BUILTIN.
                TokenRule.builtin(
                    "\\b(u_char|u_short|u_int|u_long|ushort|uint|u_quad_t|quad_t|qaddr_t|caddr_t|daddr_t|" +
                        "div_t|dev_t|fixpt_t|blkcnt_t|blksize_t|gid_t|in_addr_t|in_port_t|ino_t|key_t|" +
                        "mode_t|nlink_t|id_t|pid_t|off_t|segsz_t|swblk_t|uid_t|id_t|clock_t|size_t|" +
                        "ssize_t|time_t|useconds_t|suseconds_t)\\b"
                ),
                TokenRule.builtin(
                    "\\b(pthread_attr_t|pthread_cond_t|pthread_condattr_t|pthread_mutex_t|" +
                        "pthread_mutexattr_t|pthread_once_t|pthread_rwlock_t|pthread_rwlockattr_t|" +
                        "pthread_t|pthread_key_t)\\b"
                ),
                TokenRule.builtin(
                    "(?x) \\b\n(int8_t|int16_t|int32_t|int64_t|uint8_t|uint16_t|uint32_t|uint64_t|" +
                        "int_least8_t\n|int_least16_t|int_least32_t|int_least64_t|uint_least8_t|" +
                        "uint_least16_t|uint_least32_t\n|uint_least64_t|int_fast8_t|int_fast16_t|" +
                        "int_fast32_t|int_fast64_t|uint_fast8_t\n|uint_fast16_t|uint_fast32_t|" +
                        "uint_fast64_t|intptr_t|uintptr_t|intmax_t|intmax_t\n|uintmax_t|uintmax_t)\n\\b"
                ),
                TokenRule.builtin(
                    "(?x) \\b\n(AbsoluteTime|Boolean|Byte|ByteCount|ByteOffset|BytePtr|CompTimeValue|" +
                        "ConstLogicalAddress|ConstStrFileNameParam\n|ConstStringPtr|Duration|Fixed|" +
                        "FixedPtr|Float32|Float32Point|Float64|Float80|Float96|FourCharCode|Fract|" +
                        "FractPtr\n|Handle|ItemCount|LogicalAddress|OptionBits|OSErr|OSStatus|OSType|" +
                        "OSTypePtr|PhysicalAddress|ProcessSerialNumber\n|ProcessSerialNumberPtr|" +
                        "ProcHandle|Ptr|ResType|ResTypePtr|ShortFixed|ShortFixedPtr|SignedByte|SInt16|" +
                        "SInt32|SInt64\n|SInt8|Size|StrFileName|StringHandle|StringPtr|TimeBase|" +
                        "TimeRecord|TimeScale|TimeValue|TimeValue64|UInt16|UInt32\n|UInt64|UInt8|UniChar|" +
                        "UniCharCount|UniCharCountPtr|UniCharPtr|UnicodeScalarValue|UniversalProcHandle|" +
                        "UniversalProcPtr\n|UnsignedFixed|UnsignedFixedPtr|UnsignedWide|UTF16Char|" +
                        "UTF32Char|UTF8Char)\n\\b"
                ),
                TokenRule.builtin("\\b([A-Za-z0-9_]+_t)\\b"),
                // #numbers — the bundle re-parses the whole match to split prefix, digits and suffix; every
                // branch of that is constant.numeric.*, so the outer match maps to NUMBER as a unit
                TokenRule.number("(?<!\\w)\\.?\\d(?:(?:[0-9a-zA-Z_\\.]|')|(?<=[eEpP])[+-])*"),
            ),
    )
