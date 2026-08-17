// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns ported from plugins/textmate/lib/bundles/css/syntaxes/css.tmLanguage.json.
//
// Three lookarounds are ours, not the bundle's, and each stands in for context the grammar tree supplies.
// Property names need `(?=\s*:)` or `a:hover` reads `a` as a property; tag names need `(?!\s*[};])` or
// `display: table }` reads `table` as a tag. Around 20 words are on both lists, and rule order cannot
// separate them: whichever goes first misreads the other position. Hex colors need `(?![^;{}]*\{)` or
// `#abc { }` reads the id selector as a color.
//
// Differences you can see:
//  - Class and id selectors are colored as types, pseudo-classes and -elements as builtins.
//  - calc() arithmetic operators, standalone `even` and `odd`, :lang() language ranges, unquoted attribute
//    values, the ignore-case modifier, namespace prefixes and keyframe offsets stay plain. Each is
//    unanchored and only safe inside the context the bundle reaches it from.

/** Appended to the two property-name rules; see the header. */
private const val DECLARATION = "(?=\\s*:)"

// support.type.property-name.css
private const val PROPERTY_NAMES =
    "(?xi)(?<![\\w-])(?:accent-color|additive-symbols|align-content|align-items|align-self|all|animation|" +
        "animation-delay|animation-direction|animation-duration|animation-fill-mode|animation-iteration-count|" +
        "animation-name|animation-play-state|animation-timing-function|aspect-ratio|backdrop-filter|" +
        "backface-visibility|background|background-attachment|background-blend-mode|background-clip|" +
        "background-color|background-image|background-origin|background-position|background-position-[xy]|" +
        "background-repeat|background-size|bleed|block-size|border|border-block-end|border-block-end-color|" +
        "border-block-end-style|border-block-end-width|border-block-start|border-block-start-color|" +
        "border-block-start-style|border-block-start-width|border-bottom|border-bottom-color|" +
        "border-bottom-left-radius|border-bottom-right-radius|border-bottom-style|border-bottom-width|" +
        "border-collapse|border-color|border-end-end-radius|border-end-start-radius|border-image|" +
        "border-image-outset|border-image-repeat|border-image-slice|border-image-source|border-image-width|" +
        "border-inline-end|border-inline-end-color|border-inline-end-style|border-inline-end-width|" +
        "border-inline-start|border-inline-start-color|border-inline-start-style|border-inline-start-width|" +
        "border-left|border-left-color|border-left-style|border-left-width|border-radius|border-right|" +
        "border-right-color|border-right-style|border-right-width|border-spacing|border-start-end-radius|" +
        "border-start-start-radius|border-style|border-top|border-top-color|border-top-left-radius|" +
        "border-top-right-radius|border-top-style|border-top-width|border-width|bottom|box-decoration-break|" +
        "box-shadow|box-sizing|break-after|break-before|break-inside|caption-side|caret-color|clear|clip|clip-path|" +
        "clip-rule|color|color-adjust|color-interpolation-filters|color-scheme|column-count|column-fill|column-gap|" +
        "column-rule|column-rule-color|column-rule-style|column-rule-width|column-span|column-width|columns|" +
        "contain|container|container-name|container-type|content|counter-increment|counter-reset|cursor|direction|" +
        "display|empty-cells|enable-background|fallback|fill|fill-opacity|fill-rule|filter|flex|flex-basis|" +
        "flex-direction|flex-flow|flex-grow|flex-shrink|flex-wrap|float|flood-color|flood-opacity|font|" +
        "font-display|font-family|font-feature-settings|font-kerning|font-language-override|font-optical-sizing|" +
        "font-size|font-size-adjust|font-stretch|font-style|font-synthesis|font-variant|font-variant-alternates|" +
        "font-variant-caps|font-variant-east-asian|font-variant-ligatures|font-variant-numeric|" +
        "font-variant-position|font-variation-settings|font-weight|gap|glyph-orientation-horizontal|" +
        "glyph-orientation-vertical|grid|grid-area|grid-auto-columns|grid-auto-flow|grid-auto-rows|grid-column|" +
        "grid-column-end|grid-column-gap|grid-column-start|grid-gap|grid-row|grid-row-end|grid-row-gap|" +
        "grid-row-start|grid-template|grid-template-areas|grid-template-columns|grid-template-rows|" +
        "hanging-punctuation|height|hyphens|image-orientation|image-rendering|image-resolution|ime-mode|" +
        "initial-letter|initial-letter-align|inline-size|inset|inset-block|inset-block-end|inset-block-start|" +
        "inset-inline|inset-inline-end|inset-inline-start|isolation|justify-content|justify-items|justify-self|" +
        "kerning|left|letter-spacing|lighting-color|line-break|line-clamp|line-height|list-style|list-style-image|" +
        "list-style-position|list-style-type|margin|margin-block|margin-block-end|margin-block-start|margin-bottom|" +
        "margin-inline|margin-inline-end|margin-inline-start|margin-left|margin-right|margin-top|marker-end|" +
        "marker-mid|marker-start|marks|mask|mask-border|mask-border-mode|mask-border-outset|mask-border-repeat|" +
        "mask-border-slice|mask-border-source|mask-border-width|mask-clip|mask-composite|mask-image|mask-mode|" +
        "mask-origin|mask-position|mask-repeat|mask-size|mask-type|max-block-size|max-height|max-inline-size|" +
        "max-lines|max-width|max-zoom|min-block-size|min-height|min-inline-size|min-width|min-zoom|mix-blend-mode|" +
        "negative|object-fit|object-position|offset|offset-anchor|offset-distance|offset-path|offset-position|" +
        "offset-rotation|opacity|order|orientation|orphans|outline|outline-color|outline-offset|outline-style|" +
        "outline-width|overflow|overflow-anchor|overflow-block|overflow-inline|overflow-wrap|overflow-[xy]|" +
        "overscroll-behavior|overscroll-behavior-block|overscroll-behavior-inline|overscroll-behavior-[xy]|pad|" +
        "padding|padding-block|padding-block-end|padding-block-start|padding-bottom|padding-inline|" +
        "padding-inline-end|padding-inline-start|padding-left|padding-right|padding-top|page-break-after|" +
        "page-break-before|page-break-inside|paint-order|perspective|perspective-origin|place-content|place-items|" +
        "place-self|pointer-events|position|prefix|quotes|range|resize|right|rotate|row-gap|ruby-align|ruby-merge|" +
        "ruby-position|scale|scroll-behavior|scroll-margin|scroll-margin-block|scroll-margin-block-end|" +
        "scroll-margin-block-start|scroll-margin-bottom|scroll-margin-inline|scroll-margin-inline-end|" +
        "scroll-margin-inline-start|scroll-margin-left|scroll-margin-right|scroll-margin-top|scroll-padding|" +
        "scroll-padding-block|scroll-padding-block-end|scroll-padding-block-start|scroll-padding-bottom|" +
        "scroll-padding-inline|scroll-padding-inline-end|scroll-padding-inline-start|scroll-padding-left|" +
        "scroll-padding-right|scroll-padding-top|scroll-snap-align|scroll-snap-coordinate|scroll-snap-destination|" +
        "scroll-snap-stop|scroll-snap-type|scrollbar-color|scrollbar-gutter|scrollbar-width|" +
        "shape-image-threshold|shape-margin|shape-outside|shape-rendering|size|speak-as|src|stop-color|" +
        "stop-opacity|stroke|stroke-dasharray|stroke-dashoffset|stroke-linecap|stroke-linejoin|stroke-miterlimit|" +
        "stroke-opacity|stroke-width|suffix|symbols|system|tab-size|table-layout|text-align|text-align-last|" +
        "text-anchor|text-combine-upright|text-decoration|text-decoration-color|text-decoration-line|" +
        "text-decoration-skip|text-decoration-skip-ink|text-decoration-style|text-decoration-thickness|" +
        "text-emphasis|text-emphasis-color|text-emphasis-position|text-emphasis-style|text-indent|text-justify|" +
        "text-orientation|text-overflow|text-rendering|text-shadow|text-size-adjust|text-transform|" +
        "text-underline-offset|text-underline-position|top|touch-action|transform|transform-box|transform-origin|" +
        "transform-style|transition|transition-delay|transition-duration|transition-property|" +
        "transition-timing-function|translate|unicode-bidi|unicode-range|user-select|user-zoom|vertical-align|" +
        "visibility|white-space|widows|width|will-change|word-break|word-spacing|word-wrap|writing-mode|z-index|" +
        "zoom|alignment-baseline|baseline-shift|clip-rule|color-interpolation|color-interpolation-filters|" +
        "color-profile|color-rendering|cx|cy|dominant-baseline|enable-background|fill|fill-opacity|fill-rule|" +
        "flood-color|flood-opacity|glyph-orientation-horizontal|glyph-orientation-vertical|height|kerning|" +
        "lighting-color|marker-end|marker-mid|marker-start|r|rx|ry|shape-rendering|stop-color|stop-opacity|stroke|" +
        "stroke-dasharray|stroke-dashoffset|stroke-linecap|stroke-linejoin|stroke-miterlimit|stroke-opacity|" +
        "stroke-width|text-anchor|width|x|y|adjust|after|align|align-last|alignment|alignment-adjust|appearance|" +
        "attachment|azimuth|background-break|balance|baseline|before|bidi|binding|bookmark|bookmark-label|" +
        "bookmark-level|bookmark-target|border-length|bottom-color|bottom-left-radius|bottom-right-radius|" +
        "bottom-style|bottom-width|box|box-align|box-direction|box-flex|box-flex-group|box-lines|box-ordinal-group|" +
        "box-orient|box-pack|break|character|collapse|column|column-break-after|column-break-before|count|counter|" +
        "crop|cue|cue-after|cue-before|decoration|decoration-break|delay|display-model|display-role|down|drop|" +
        "drop-initial-after-adjust|drop-initial-after-align|drop-initial-before-adjust|drop-initial-before-align|" +
        "drop-initial-size|drop-initial-value|duration|elevation|emphasis|family|fit|fit-position|flex-group|" +
        "float-offset|gap|grid-columns|grid-rows|hanging-punctuation|header|hyphenate|hyphenate-after|" +
        "hyphenate-before|hyphenate-character|hyphenate-lines|hyphenate-resource|icon|image|increment|indent|" +
        "index|initial-after-adjust|initial-after-align|initial-before-adjust|initial-before-align|initial-size|" +
        "initial-value|inline-box-align|iteration-count|justify|label|left-color|left-style|left-width|length|" +
        "level|line|line-stacking|line-stacking-ruby|line-stacking-shift|line-stacking-strategy|lines|list|mark|" +
        "mark-after|mark-before|marks|marquee|marquee-direction|marquee-play-count|marquee-speed|marquee-style|max|" +
        "min|model|move-to|name|nav|nav-down|nav-index|nav-left|nav-right|nav-up|new|numeral|offset|ordinal-group|" +
        "orient|origin|overflow-style|overhang|pack|page|page-policy|pause|pause-after|pause-before|phonemes|pitch|" +
        "pitch-range|play-count|play-during|play-state|point|presentation|presentation-level|profile|property|" +
        "punctuation|punctuation-trim|radius|rate|rendering-intent|repeat|replace|reset|resolution|resource|" +
        "respond-to|rest|rest-after|rest-before|richness|right-color|right-style|right-width|role|rotation|" +
        "rotation-point|rows|ruby|ruby-overhang|ruby-span|rule|rule-color|rule-style|rule-width|shadow|size|" +
        "size-adjust|sizing|space|space-collapse|spacing|span|speak|speak-header|speak-numeral|speak-punctuation|" +
        "speech|speech-rate|speed|stacking|stacking-ruby|stacking-shift|stacking-strategy|stress|stretch|" +
        "string-set|style|style-image|style-position|style-type|target|target-name|target-new|target-position|text|" +
        "text-height|text-justify|text-outline|text-replace|text-wrap|timing-function|top-color|top-left-radius|" +
        "top-right-radius|top-style|top-width|trim|unicode|up|user-select|variant|voice|voice-balance|" +
        "voice-duration|voice-family|voice-pitch|voice-pitch-range|voice-rate|voice-stress|voice-volume|volume|" +
        "weight|white|white-space-collapse|word|wrap)(?![\\w-])"

// support.type.vendored.property-name.css, and — byte for byte the same pattern —
// support.constant.vendored.property-value.css
private const val VENDORED =
    "(?<![\\w-])(?i:-(?:ah|apple|atsc|epub|hp|khtml|moz|ms|o|rim|ro|tc|wap|webkit|xv)|(?:mso|prince))-[a-zA-Z-]+"

// support.constant.property-value.css
private const val PROPERTY_VALUE_KEYWORDS =
    "(?xi)(?<![\\w-])(above|absolute|active|add|additive|after-edge|alias|all|all-petite-caps|all-scroll|" +
        "all-small-caps|alpha|alphabetic|alternate|alternate-reverse|always|antialiased|auto|auto-fill|auto-fit|" +
        "auto-pos|available|avoid|avoid-column|avoid-page|avoid-region|backwards|balance|baseline|before-edge|" +
        "below|bevel|bidi-override|blink|block|block-axis|block-start|block-end|bold|bolder|border|border-box|both|" +
        "bottom|bottom-outside|break-all|break-word|bullets|butt|capitalize|caption|cell|center|central|char|" +
        "circle|clip|clone|close-quote|closest-corner|closest-side|col-resize|collapse|color|color-burn|" +
        "color-dodge|column|column-reverse|common-ligatures|compact|condensed|contain|content|content-box|" +
        "contents|context-menu|contextual|copy|cover|crisp-edges|crispEdges|crosshair|cyclic|dark|darken|dashed|" +
        "decimal|default|dense|diagonal-fractions|difference|digits|disabled|disc|discretionary-ligatures|" +
        "distribute|distribute-all-lines|distribute-letter|distribute-space|dot|dotted|double|double-circle|" +
        "downleft|downright|e-resize|each-line|ease|ease-in|ease-in-out|ease-out|economy|ellipse|ellipsis|embed|" +
        "end|evenodd|ew-resize|exact|exclude|exclusion|expanded|extends|extra-condensed|extra-expanded|fallback|" +
        "farthest-corner|farthest-side|fill|fill-available|fill-box|filled|fit-content|fixed|flat|flex|flex-end|" +
        "flex-start|flip|flow|flow-root|forwards|freeze|from-image|full-width|geometricPrecision|georgian|grab|" +
        "grabbing|grayscale|grid|groove|hand|hanging|hard-light|help|hidden|hide|historical-forms|" +
        "historical-ligatures|horizontal|horizontal-tb|hue|icon|ideograph-alpha|ideograph-numeric|" +
        "ideograph-parenthesis|ideograph-space|ideographic|inactive|infinite|inherit|initial|inline|inline-axis|" +
        "inline-block|inline-end|inline-flex|inline-grid|inline-list-item|inline-start|inline-table|inset|inside|" +
        "inter-character|inter-ideograph|inter-word|intersect|invert|isolate|isolate-override|italic|jis04|jis78|" +
        "jis83|jis90|justify|justify-all|kannada|keep-all|landscape|large|larger|left|light|lighten|lighter|line|" +
        "line-edge|line-through|linear|linearRGB|lining-nums|list-item|local|loose|lowercase|lr|lr-tb|ltr|" +
        "luminance|luminosity|main-size|mandatory|manipulation|manual|margin-box|match-parent|match-source|" +
        "mathematical|max-content|medium|menu|message-box|middle|min-content|miter|mixed|move|multiply|n-resize|" +
        "narrower|ne-resize|nearest-neighbor|nesw-resize|newspaper|no-change|no-clip|no-close-quote|" +
        "no-common-ligatures|no-contextual|no-discretionary-ligatures|no-drop|no-historical-ligatures|" +
        "no-open-quote|no-repeat|none|nonzero|normal|not-allowed|nowrap|ns-resize|numbers|numeric|nw-resize|" +
        "nwse-resize|oblique|oldstyle-nums|open|open-quote|optimizeLegibility|optimizeQuality|optimizeSpeed|" +
        "optional|ordinal|outset|outside|over|overlay|overline|padding|padding-box|page|painted|pan-down|pan-left|" +
        "pan-right|pan-up|pan-x|pan-y|paused|petite-caps|pixelated|plaintext|pointer|portrait|pre|pre-line|" +
        "pre-wrap|preserve-3d|progress|progressive|proportional-nums|proportional-width|proximity|radial|recto|" +
        "region|relative|remove|repeat|repeat-[xy]|reset-size|reverse|revert|revert-layer|ridge|right|rl|rl-tb|" +
        "round|row|row-resize|row-reverse|row-severse|rtl|ruby|ruby-base|ruby-base-container|ruby-text|" +
        "ruby-text-container|run-in|running|s-resize|saturation|scale-down|screen|scroll|scroll-position|se-resize|" +
        "semi-condensed|semi-expanded|separate|sesame|show|sideways|sideways-left|sideways-lr|sideways-right|" +
        "sideways-rl|simplified|slashed-zero|slice|small|small-caps|small-caption|smaller|smooth|soft-light|solid|" +
        "space|space-around|space-between|space-evenly|spell-out|square|sRGB|stacked-fractions|start|static|" +
        "status-bar|swap|step-end|step-start|sticky|stretch|strict|stroke|stroke-box|style|sub|subgrid|" +
        "subpixel-antialiased|subtract|super|sw-resize|symbolic|table|table-caption|table-cell|table-column|" +
        "table-column-group|table-footer-group|table-header-group|table-row|table-row-group|tabular-nums|tb|tb-rl|" +
        "text|text-after-edge|text-before-edge|text-bottom|text-top|thick|thin|titling-caps|top|top-outside|touch|" +
        "traditional|transparent|triangle|ultra-condensed|ultra-expanded|under|underline|unicase|unset|upleft|" +
        "uppercase|upright|use-glyph-orientation|use-script|verso|vertical|vertical-ideographic|vertical-lr|" +
        "vertical-rl|vertical-text|view-box|visible|visibleFill|visiblePainted|visibleStroke|w-resize|wait|wavy|" +
        "weight|whitespace|wider|words|wrap|wrap-reverse|x|x-large|x-small|xx-large|xx-small|y|zero|zoom-in|" +
        "zoom-out)(?![\\w-])"

// support.constant.property-value.list-style-type.css
private const val LIST_STYLE_TYPE_KEYWORDS =
    "(?xi)(?<![\\w-])(arabic-indic|armenian|bengali|cambodian|circle|cjk-decimal|cjk-earthly-branch|" +
        "cjk-heavenly-stem|cjk-ideographic|decimal|decimal-leading-zero|devanagari|disc|disclosure-closed|" +
        "disclosure-open|ethiopic-halehame-am|ethiopic-halehame-ti-e[rt]|ethiopic-numeric|georgian|gujarati|" +
        "gurmukhi|hangul|hangul-consonant|hebrew|hiragana|hiragana-iroha|japanese-formal|japanese-informal|kannada|" +
        "katakana|katakana-iroha|khmer|korean-hangul-formal|korean-hanja-formal|korean-hanja-informal|lao|" +
        "lower-alpha|lower-armenian|lower-greek|lower-latin|lower-roman|malayalam|mongolian|myanmar|oriya|persian|" +
        "simp-chinese-formal|simp-chinese-informal|square|tamil|telugu|thai|tibetan|trad-chinese-formal|" +
        "trad-chinese-informal|upper-alpha|upper-armenian|upper-latin|upper-roman|urdu)(?![\\w-])"

// entity.name.tag.css
private const val TAG_NAMES =
    "(?m)(?xi)(?<![\\w:-])(?:a|abbr|acronym|address|applet|area|article|aside|audio|b|base|basefont|bdi|bdo|" +
        "bgsound|big|blink|blockquote|body|br|button|canvas|caption|center|cite|code|col|colgroup|command|content|" +
        "data|datalist|dd|del|details|dfn|dialog|dir|div|dl|dt|element|em|embed|fieldset|figcaption|figure|font|" +
        "footer|form|frame|frameset|h[1-6]|head|header|hgroup|hr|html|i|iframe|image|img|input|ins|isindex|kbd|" +
        "keygen|label|legend|li|link|listing|main|map|mark|marquee|math|menu|menuitem|meta|meter|multicol|nav|" +
        "nextid|nobr|noembed|noframes|noscript|object|ol|optgroup|option|output|p|param|picture|plaintext|pre|" +
        "progress|q|rb|rp|rt|rtc|ruby|s|samp|script|section|select|shadow|slot|small|source|spacer|span|strike|" +
        "strong|style|sub|summary|sup|table|tbody|td|template|textarea|tfoot|th|thead|time|title|tr|track|tt|u|ul|" +
        "var|video|wbr|xmp|altGlyph|altGlyphDef|altGlyphItem|animate|animateColor|animateMotion|animateTransform|" +
        "circle|clipPath|color-profile|cursor|defs|desc|discard|ellipse|feBlend|feColorMatrix|feComponentTransfer|" +
        "feComposite|feConvolveMatrix|feDiffuseLighting|feDisplacementMap|feDistantLight|feDropShadow|feFlood|" +
        "feFuncA|feFuncB|feFuncG|feFuncR|feGaussianBlur|feImage|feMerge|feMergeNode|feMorphology|feOffset|" +
        "fePointLight|feSpecularLighting|feSpotLight|feTile|feTurbulence|filter|font-face|font-face-format|" +
        "font-face-name|font-face-src|font-face-uri|foreignObject|g|glyph|glyphRef|hatch|hatchpath|hkern|line|" +
        "linearGradient|marker|mask|mesh|meshgradient|meshpatch|meshrow|metadata|missing-glyph|mpath|path|pattern|" +
        "polygon|polyline|radialGradient|rect|set|solidcolor|stop|svg|switch|symbol|text|textPath|tref|tspan|use|" +
        "view|vkern|annotation|annotation-xml|maction|maligngroup|malignmark|math|menclose|merror|mfenced|mfrac|" +
        "mglyph|mi|mlabeledtr|mlongdiv|mmultiscripts|mn|mo|mover|mpadded|mphantom|mroot|mrow|ms|mscarries|mscarry|" +
        "msgroup|msline|mspace|msqrt|msrow|mstack|mstyle|msub|msubsup|msup|mtable|mtd|mtext|mtr|munder|munderover|" +
        "semantics)" +
        // Ours, not the bundle's: stands in for the selector context #tag-names gets from the
        // grammar tree. A tag name in selector position never precedes `}` or `;`; a property value
        // at the end of a block always does.
        "(?!\\s*[};])" +
        "(?=[+~>\\s,.\\#|){:\\[]|/\\*|\$)"

// support.constant.color.w3c-extended-color-name.css
private const val EXTENDED_COLOR_NAMES =
    "(?xi)(?<![\\w-])(aliceblue|antiquewhite|aquamarine|azure|beige|bisque|blanchedalmond|blueviolet|brown|" +
        "burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|" +
        "darkgoldenrod|darkgray|darkgreen|darkgrey|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|" +
        "darkred|darksalmon|darkseagreen|darkslateblue|darkslategray|darkslategrey|darkturquoise|darkviolet|" +
        "deeppink|deepskyblue|dimgray|dimgrey|dodgerblue|firebrick|floralwhite|forestgreen|gainsboro|ghostwhite|" +
        "gold|goldenrod|greenyellow|grey|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|" +
        "lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgray|lightgreen|" +
        "lightgrey|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategray|lightslategrey|lightsteelblue|" +
        "lightyellow|limegreen|linen|magenta|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|" +
        "mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|" +
        "moccasin|navajowhite|oldlace|olivedrab|orangered|orchid|palegoldenrod|palegreen|paleturquoise|" +
        "palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|rebeccapurple|rosybrown|royalblue|" +
        "saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|skyblue|slateblue|slategray|slategrey|snow|" +
        "springgreen|steelblue|tan|thistle|tomato|transparent|turquoise|violet|wheat|whitesmoke|yellowgreen)" +
        "(?![\\w-])"

// support.type.property-name.media.css + support.type.vendored.property-name.media.css
private const val MEDIA_FEATURES =
    "(?m)(?xi)(?<=^|\\s|\\(|\\*/)(?:((?:min-|max-)?(?:height|width|aspect-ratio|color|color-index|monochrome|" +
        "resolution)|grid|scan|orientation|display-mode|hover)|((?:min-|max-)?device-(?:height|width|" +
        "aspect-ratio))|((?:[-_](?:webkit|apple|khtml|epub|moz|ms|o|xv|ah|rim|atsc|hp|tc|wap|ro)|(?:mso|prince))-" +
        "[\\w-]+(?=\\s*(?:/\\*(?:[^*]|\\*[^/])*\\*/)?\\s*[:)])))(?=\\s|\$|[><:=]|\\)|/\\*)"

// constant.numeric.css, with keyword.other.unit.*.css on the trailing unit
private const val NUMERIC =
    "(?xi)(?<![\\w-])[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:(?<=[0-9])E[-+]?[0-9]+)?(?:(%)|(deg|grad|rad|turn|" +
        "Hz|kHz|ch|cm|em|ex|fr|in|mm|mozmm|pc|pt|px|q|rem|rch|rex|rlh|ic|ric|rcap|vh|vw|vb|vi|svh|svw|svb|svi|dvh|" +
        "dvw|dvb|dvi|lvh|lvw|lvb|lvi|vmax|vmin|cqw|cqi|cqh|cqb|cqmin|cqmax|dpi|dpcm|dppx|s|ms)\\b)?"

// entity.other.attribute-name.pseudo-class.css
private const val PSEUDO_CLASSES =
    "(?xi)(:)(:*)(?:active|any-link|checked|default|disabled|empty|enabled|first|(?:first|last|only)-(?:child|" +
        "of-type)|focus|focus-visible|focus-within|fullscreen|host|hover|in-range|indeterminate|invalid|left|link|" +
        "optional|out-of-range|read-only|read-write|required|right|root|scope|target|unresolved|valid|visited)" +
        "(?![\\w-]|\\s*[;}])"

// entity.other.attribute-name.pseudo-element.css
private const val PSEUDO_ELEMENTS =
    "(?xi)(?:(::?)(?:after|before|first-letter|first-line|(?:-(?:ah|apple|atsc|epub|hp|khtml|moz|ms|o|rim|ro|tc|" +
        "wap|webkit|xv)|(?:mso|prince))-[a-z-]+)|(::)(?:backdrop|content|grammar-error|marker|placeholder|" +
        "selection|shadow|spelling-error))(?![\\w-]|\\s*[;}])"

// support.function.misc.css, the long list from #functions
private const val MISC_FUNCTIONS =
    "(?xi)(?<![\\w-])(annotation|attr|blur|brightness|character-variant|clamp|contrast|counters?|cross-fade|" +
        "drop-shadow|element|fit-content|format|grayscale|hue-rotate|color-mix|image-set|invert|local|max|min|" +
        "minmax|opacity|ornaments|repeat|saturate|sepia|styleset|stylistic|swash|symbols|cos|sin|tan|acos|asin|" +
        "atan|atan2|hypot|sqrt|pow|log|exp|abs|sign)(\\()"

// A CSS custom property: variable.css in #rule-list-innards, variable.argument.css inside var(). The two patterns
// are identical apart from the leading (?<![\w-]), which is kept.
private const val CUSTOM_PROPERTY =
    "(?x)(?<![\\w-])--(?:[-a-zA-Z_]|[^\\x00-\\x7F])(?:[-a-zA-Z0-9_]|[^\\x00-\\x7F]|\\\\(?:[0-9a-fA-F]{1,6}|.))*"

internal val CSS =
    LanguageGrammar(
        name = "css",
        rules =
            listOf(
                // comment.block.css — begin `/\*`, end `\*/`
                TokenRule.comment("/\\*(?:[^*]|\\*[^/])*\\*/"),
                // string.quoted.double.css / string.quoted.single.css — begin `"`, end `"|(?<!\\)(?=$|\n)`, with
                // #escapes as the body
                TokenRule.string("(?m)\"(?:\\\\(?:[0-9a-fA-F]{1,6}|.)|[^\"\\\\\\r\\n])*+(?:\"|(?<!\\\\)(?=\$))"),
                TokenRule.string("(?m)'(?:\\\\(?:[0-9a-fA-F]{1,6}|.)|[^'\\\\\\r\\n])*+(?:'|(?<!\\\\)(?=\$))"),
                // constant.character.escape.codepoint.css / .newline.css / .css
                TokenRule.constant("\\\\[0-9a-fA-F]{1,6}"),
                TokenRule.constant("(?m)\\\\\$\\s*"),
                TokenRule.constant("\\\\."),
                // constant.other.color.rgb-value.hex.css. Ours, not the bundle's: (?![^;{}]*\{) stands in for
                // the declaration context #property-values gets from the tree. `#abc` is both a hex color and
                // an id selector, and only a selector is followed by `{`.
                TokenRule.constant("(#)(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b(?![^;{}]*\\{)"),
                // constant.other.unicode-range.css — before entity.name.tag.css, which would otherwise claim the
                // `U` of `U+0025` as the HTML `<u>` element (`+` is in its lookahead set)
                TokenRule.constant("(?<![\\w-])[Uu]\\+[0-9A-Fa-f?]{1,6}(?:(-)[0-9A-Fa-f]{1,6})?(?![\\w-])"),
                // support.type.property-name.css and support.type.vendored.property-name.css
                TokenRule.propertyKey(PROPERTY_NAMES + DECLARATION),
                TokenRule.propertyKey(VENDORED + DECLARATION),
                // variable.css — a custom property declaration
                TokenRule.propertyKey(CUSTOM_PROPERTY),
                // keyword.control.at-rule.css — the bundle's generic fallback, which subsumes every named at-rule
                TokenRule.keyword("(?i)(@)[\\w-]+"),
                // keyword.other.important.css
                TokenRule.keyword("!\\s*important(?![\\w-])"),
                // entity.name.tag.css
                TokenRule.keyword(TAG_NAMES),
                // constant.numeric.other.density.css, meta.ratio.css (two constant.numeric.css around a
                // keyword.operator.arithmetic.css) and constant.numeric.css itself. These sit ahead of
                // entity.other.attribute-name.class.css because the bundle keeps `.5` away from the class rule with
                // invalid.illegal.bad-identifier.css, which we drop along with the rest of invalid.*; and ahead of
                // #combinators so `+5px` is a signed number rather than a combinator and a number.
                TokenRule.number("(?m)(?i)(?<=[,\\s\"]|\\*/|^)\\d+x(?=[\\s,\"')]|/\\*|\$)"),
                TokenRule(
                    "(\\d+)\\s*(/)\\s*(\\d+)",
                    mapOf(1 to TokenType.NUMBER, 2 to TokenType.OPERATOR, 3 to TokenType.NUMBER),
                ),
                TokenRule(NUMERIC, mapOf(0 to TokenType.NUMBER, 1 to TokenType.KEYWORD, 2 to TokenType.KEYWORD)),
                // entity.other.attribute-name.class.css / .id.css
                TokenRule.type(
                    "(?m)(?x)(\\.)((?:[-a-zA-Z_0-9]|[^\\x00-\\x7F]|\\\\(?:[0-9a-fA-F]{1,6}|.))+)" +
                        "(?=\$|[\\s,.\\#)\\[:{>+~|]|/\\*)"
                ),
                TokenRule.type(
                    "(?m)(?x)(\\#)(-?(?![0-9])(?:[-a-zA-Z0-9_]|[^\\x00-\\x7F]|\\\\(?:[0-9a-fA-F]{1,6}|.))+)" +
                        "(?=\$|[\\s,.\\#)\\[:{>+~|]|/\\*)"
                ),
                // entity.other.attribute-name.css, fused with meta.attribute-selector.css's `begin: "\\["` so the
                // name is only recognized inside brackets
                TokenRule(
                    "(?x)\\[\\s*(-?(?!\\d)(?>[\\w-]|[^\\x00-\\x7F]|\\\\(?:[0-9a-fA-F]{1,6}|.))+)\\s*" +
                        "(?=[~|^\\]\$*=]|/\\*)",
                    mapOf(1 to TokenType.PROPERTY_KEY),
                ),
                // keyword.operator.pattern.css
                TokenRule.operator("[~|^\$*]?="),
                // entity.other.attribute-name.pseudo-class.css / .pseudo-element.css
                TokenRule.builtin(PSEUDO_CLASSES),
                TokenRule.builtin(PSEUDO_ELEMENTS),
                // #functional-pseudo-classes. The nth rule is fused with its body so that constant.numeric.css and
                // support.constant.parity.css keep the `(` … `)` context they need; the others only need their name.
                TokenRule(
                    "(?i)((:)nth-(?:last-)?(?:child|of-type))\\(\\s*(?:([+-]?(?:\\d+n?|n)(?:\\s*[+-]\\s*\\d+)?)|" +
                        "(even|odd))\\s*\\)",
                    mapOf(1 to TokenType.BUILTIN, 3 to TokenType.NUMBER, 4 to TokenType.BUILTIN),
                ),
                TokenRule("(?i)((:)nth-(?:last-)?(?:child|of-type))(?=\\()", mapOf(1 to TokenType.BUILTIN)),
                TokenRule("(?i)((:)dir)(\\()", mapOf(1 to TokenType.BUILTIN)),
                TokenRule("(?i)((:)lang)(\\()", mapOf(1 to TokenType.BUILTIN)),
                TokenRule("(?i)((:)(?:not|has|matches|where|is))(\\()", mapOf(1 to TokenType.BUILTIN)),
                // support.function.*.css — calc, color, gradient, misc, shape, timing-function, transform, url, var
                TokenRule.functionCall("(?i)(?<![\\w-])(calc)(\\()"),
                TokenRule.functionCall("(?i)(?<![\\w-])(rgba?|rgb|hsla?|hsl|hwb|lab|oklab|lch|oklch|color)(\\()"),
                TokenRule.functionCall(
                    "(?xi)(?<![\\w-])((?:-webkit-|-moz-|-o-)?(?:repeating-)?(?:linear|radial|conic)-gradient)(\\()"
                ),
                TokenRule.functionCall(MISC_FUNCTIONS),
                TokenRule.functionCall("(?i)(?<![\\w-])(circle|ellipse|inset|polygon|rect)(\\()"),
                TokenRule.functionCall("(?i)(?<![\\w-])(cubic-bezier|steps)(\\()"),
                TokenRule.functionCall(
                    "(?xi)(?<![\\w-])((?:translate|scale|rotate)(?:[XYZ]|3D)?|matrix(?:3D)?|skew[XY]?|perspective)" +
                        "(\\()"
                ),
                TokenRule.functionCall("(?i)(?<![\\w@-])(url)(\\()"),
                // support.function.document-rule.css, from #document-rule's header
                TokenRule.functionCall("(?i)(?<![\\w-])(url-prefix|domain|regexp)(\\()"),
                TokenRule.functionCall("(?i)(?<![\\w-])(var)(\\()"),
                // support.constant.property-value.css, .list-style-type.css, vendored, .font-name.css. These come
                // before entity.name.tag.custom.css so hyphenated keywords are not mistaken for custom elements.
                TokenRule.builtin(PROPERTY_VALUE_KEYWORDS),
                TokenRule.builtin(LIST_STYLE_TYPE_KEYWORDS),
                TokenRule.builtin(VENDORED),
                TokenRule.builtin(
                    "(?<![\\w-])(?i:arial|century|comic|courier|garamond|georgia|helvetica|impact|lucida|symbol|" +
                        "system-ui|system|tahoma|times|trebuchet|ui-monospace|ui-rounded|ui-sans-serif|ui-serif|" +
                        "utopia|verdana|webdings|sans-serif|serif|monospace)(?![\\w-])"
                ),
                // support.constant.color.w3c-standard-color-name.css / .w3c-extended-color-name.css / .current.css
                TokenRule.builtin(
                    "(?i)(?<![\\w-])(aqua|black|blue|fuchsia|gray|green|lime|maroon|navy|olive|orange|purple|red|" +
                        "silver|teal|white|yellow)(?![\\w-])"
                ),
                TokenRule.builtin(EXTENDED_COLOR_NAMES),
                TokenRule.builtin("(?i)(?<![\\w-])currentColor(?![\\w-])"),
                // support.constant.media.css — group 2 is invalid.deprecated.constant.media.css and is dropped
                TokenRule(
                    "(?m)(?xi)(?<=^|\\s|,|\\*/)(?:(all|print|screen|speech)|(aural|braille|embossed|handheld|" +
                        "projection|tty|tv))(?=\$|[{,\\s;]|/\\*)",
                    mapOf(1 to TokenType.BUILTIN),
                ),
                TokenRule(
                    MEDIA_FEATURES,
                    mapOf(1 to TokenType.PROPERTY_KEY, 2 to TokenType.PROPERTY_KEY, 3 to TokenType.PROPERTY_KEY),
                ),
                // support.constant.property-value.css, from #media-feature-keywords
                TokenRule.builtin(
                    "(?m)(?xi)(?<=^|\\s|:|\\*/)(?:portrait|landscape|progressive|interlace|fullscreen|standalone|" +
                        "minimal-ui|browser|hover)(?=\\s|\\)|\$)"
                ),
                // keyword.operator.logical.feature.$1.css and keyword.operator.logical.$1.media.css
                TokenRule("(?m)(?i)(?<=[\\s()]|^|\\*/)(and|not|or)(?=[\\s()]|/\\*|\$)", mapOf(1 to TokenType.OPERATOR)),
                TokenRule("(?m)(?i)(?<=\\s|^|,|\\*/)(only|not)(?=\\s|\\{|/\\*|\$)", mapOf(1 to TokenType.OPERATOR)),
                // keyword.operator.comparison.css
                TokenRule.operator(">=|<=|=|<|>"),
                // keyword.operator.gradient.css and keyword.operator.shape.css
                TokenRule("(?i)(?<![\\w-])(from|to|at|in|hue)(?![\\w-])", mapOf(1 to TokenType.OPERATOR)),
                TokenRule("(?m)(?i)(?<=\\s|^|\\*/)(at|round)(?=\\s|/\\*|\$)", mapOf(1 to TokenType.OPERATOR)),
                // entity.name.tag.custom.css
                TokenRule.keyword("(?x)(?<![@\\w-])(?=[a-z]\\w*-)(?:(?![A-Z])[\\w-])+(?![(\\w-])"),
                // keyword.operator.combinator.css and entity.name.tag.wildcard.css
                TokenRule.operator(">>|>|\\+|~"),
                TokenRule.keyword("\\*"),
            ),
    )
