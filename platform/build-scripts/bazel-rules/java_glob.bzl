"""The `java.nio` glob subset of the `javaglob` crate, for the analysis of a rule.

A rule that states the executable files of a home placement matches the same patterns as the collector. The subset has
literals, `*`, `**` and `{a,b}` groups. `*` matches zero or more characters other than `/`, and `**` matches across `/`.
A group matches one of its alternatives. A match covers the whole path and is case-sensitive. The functions refuse `?`,
`[`, `]`, `\\`, a nested group and an unbalanced brace, as the crate does.
"""

_STAR = "*"
_ANY = "**"

def _alternatives(pattern):
    """The patterns without groups that `pattern` stands for, each as a list of tokens, or a problem text."""
    pieces = [[""]]
    group = None
    for index in range(len(pattern)):
        character = pattern[index]
        if character in "?[]\\":
            return None, "'%s' at %d" % (character, index)
        if character == "{":
            if group != None:
                return None, "a nested '{' at %d" % index
            group = [""]
            continue
        if character == "}":
            if group == None:
                return None, "a '}' with no '{' at %d" % index
            pieces.append(group)
            pieces.append([""])
            group = None
            continue
        if group != None:
            if character == ",":
                group.append("")
            else:
                group[-1] += character
            continue
        pieces[-1][-1] += character
    if group != None:
        return None, "no '}' for the '{' at %d" % pattern.rindex("{")
    texts = [""]
    for piece in pieces:
        texts = [text + choice for text in texts for choice in piece]
    return [_tokens(text) for text in texts], None

def _tokens(text):
    tokens = []
    index = 0
    for _ in range(len(text)):
        if index >= len(text):
            break
        if text[index] == "*":
            if index + 1 < len(text) and text[index + 1] == "*":
                tokens.append(_ANY)
                index += 2
            else:
                tokens.append(_STAR)
                index += 1
            continue
        tokens.append(text[index])
        index += 1
    return tokens

def java_glob(pattern, what):
    """Compiles one pattern of the subset. `what` names the pattern in the error, such as an attribute."""
    alternatives, problem = _alternatives(pattern)
    if problem:
        fail("%s: glob \"%s\": the dev-dist plan supports only *, ** and {a,b}, but the pattern has %s" % (what, pattern, problem))
    return struct(pattern = pattern, alternatives = alternatives)

def _token_prefix(tokens):
    prefix = ""
    for token in tokens:
        if token in (_STAR, _ANY):
            break
        prefix += token
    return prefix

def _matches_tokens(tokens, path):
    if _STAR not in tokens and _ANY not in tokens:
        return "".join(tokens) == path
    if not path.startswith(_token_prefix(tokens)):
        return False

    # `matched[j]` tells whether the tokens from `i` on match the path from `j` on. The loop walks `i` backwards.
    size = len(path)
    matched = [False] * size + [True]
    for i in range(len(tokens) - 1, -1, -1):
        token = tokens[i]
        current = [False] * (size + 1)
        for j in range(size, -1, -1):
            if token == _ANY:
                current[j] = matched[j] or (j < size and current[j + 1])
            elif token == _STAR:
                current[j] = matched[j] or (j < size and path[j] != "/" and current[j + 1])
            else:
                current[j] = j < size and path[j] == token and matched[j + 1]
        matched = current
    return matched[0]

def java_glob_matches(glob, path):
    """Whether the whole `path` matches the compiled `glob`. One trailing `/` does not count, as for the JDK."""
    if len(path) > 1 and path.endswith("/"):
        path = path[:-1]
    for tokens in glob.alternatives:
        if _matches_tokens(tokens, path):
            return True
    return False

def java_globs_match(globs, path):
    """Whether any of the compiled `globs` matches the whole `path`."""
    for glob in globs:
        if java_glob_matches(glob, path):
            return True
    return False

def executable_destinations(patterns, what, plugin_directory, destinations):
    """The `destinations` below `plugin_directory` that one of `patterns` matches, sorted.

    A pattern is relative to the plugin directory. The collector applies the same patterns to the entries of a plugin
    component, and the composer checks that the placement agrees with the manifest. `what` names the patterns in an
    error.
    """
    globs = [java_glob(pattern, what) for pattern in patterns]
    if not globs:
        return []
    prefix = plugin_directory + "/"
    return sorted([
        destination
        for destination in destinations
        if destination.startswith(prefix) and java_globs_match(globs, destination[len(prefix):])
    ])
