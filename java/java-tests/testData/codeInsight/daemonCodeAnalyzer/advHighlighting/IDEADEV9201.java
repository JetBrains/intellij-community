class YouAreNotMyType {

    String[][] oldLady() {
        return  new String[][]{new <error descr="Incompatible types. Found: 'java.lang.Integer[]', required: 'java.lang.String[]'">Integer</error>[]{}};
    }
}