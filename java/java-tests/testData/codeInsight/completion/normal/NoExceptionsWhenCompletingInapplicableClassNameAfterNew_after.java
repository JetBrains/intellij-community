class Cx {
    Another F = new Cx()<caret>Another(
            f -> ((String)f).stream().anyMatch(c -> c.m(f) != null));

}

class Another {}
