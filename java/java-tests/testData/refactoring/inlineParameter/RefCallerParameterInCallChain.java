class Foo {
    void f(boolean b) {
        String project = project(b);
        if (b) {
            barrrr(project.substring(0));
        } else {
            if (true) {
                barrrr(project.substring(0));
            }
        }

    }

    private void barrrr(String <caret>pProject) {
        System.out.println(pProject);
    }

    String project (boolean b) {
        return null;
    }
}