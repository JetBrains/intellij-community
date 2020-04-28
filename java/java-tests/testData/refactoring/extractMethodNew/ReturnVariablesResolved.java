class Test {
    String test(){
        String variable = "identifier";
        <selection>if (1 == 1) return variable;
        if (2 == 1) return "literal";</selection>
        return "return";
    }
}