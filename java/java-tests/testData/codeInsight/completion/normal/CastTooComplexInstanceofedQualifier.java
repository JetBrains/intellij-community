public class Aaaaaaa {

    Object foo() {
        return null;
    }

    Object foobar() {
        return null;
    }

    void bar() {
        try {

            if (foo() instanceof String) {
                //foo().s
            }
            try {
                try {
                    try {
                        try {
                            if (foobar() instanceof String) {
                            }
                          foobar().substr<caret>
                        } finally {
                            try {
                                System.out.println("");
                            } catch (Exception e) {
                                System.out.println("");
                            }
                        }
                    } finally {
                        try {
                            try {
                                if (foo() instanceof String) {
                                    System.out.println("");
                                }
                            } finally {
                                try {
                                    System.out.println("");
                                } catch (Exception e) {
                                    System.out.println("");
                                }
                            }

                        } catch (Exception e) {
                            System.out.println("");
                        }
                    }
                } finally {
                    try {
                        System.out.println("");
                    } catch (Exception e) {
                        System.out.println("");
                    }
                }
            } finally {
                try {
                    System.out.println("");
                } catch (Exception e) {
                    System.out.println("");
                }
            }

        } finally {
            try {
                System.out.println("");
            } catch (Exception e) {
                System.out.println("");
            }
        }
    }

}
