// tt \\<error descr="Illegal Unicode escape sequence">\uuu00b</error>ro
// ken\\ua <- escaped backslash so not an unicode escape
// escape at the end 1 \
// escape at the end 2 <error descr="Illegal Unicode escape sequence">\u</error>
// escape at the end 3 <error descr="Illegal Unicode escape sequence">\uu</error>
// escape at the end 4 <error descr="Illegal Unicode escape sequence">\uu1</error>
// escape at the end 5 <error descr="Illegal Unicode escape sequence">\uu12</error>
// escape at the end 6 <error descr="Illegal Unicode escape sequence">\uu123</error>
// unicode escaped backslash does not escape next backslash -> \u005c<error descr="Illegal Unicode escape sequence">\u</error>
/*
in block comment
<error descr="Illegal Unicode escape sequence">\u</error>
\u000A <- correct escape
 */
/**
 * in javadoc comment <error descr="Illegal Unicode escape sequence">\u123</error>
 */