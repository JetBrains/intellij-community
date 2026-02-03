class MultipleFieldsSingleDeclaration {

    String s = "";
    String[] array;

    {
        array = new String[]{s};
    }

}