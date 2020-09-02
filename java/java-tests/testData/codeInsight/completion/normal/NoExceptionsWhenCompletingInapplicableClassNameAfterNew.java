class Cx {
    Another F = new C<caret>Another(
            f -> ((String)f).stream().anyMatch(c -> c.m(f) != null));

}

class Another {}
