class YouAreNotMyType {

    String[][] oldLady() {
        return  new String[][]{<error descr="Incompatible types. Found: 'java.lang.Integer[]', required: 'java.lang.String[]'">new Integer[]{}</error>};
    }
}