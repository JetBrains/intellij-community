
/// Single liner code block:
/// `Hello world`
///
/// No tags are interpreted inside them
/// `{@link java.lang.String niceLink}`
///
/// No markdown markup is interpreted inside them
/// `_Hello_ <code>`
///
/// Code span inside a link
/// [my text with `a code span`!][java.lang.String]
///
/// This is a broken inline code span
/// `Start of broken code span
///
/// end of broken code span`
class MarkdownCodeBlock {}