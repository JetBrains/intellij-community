package pkg;

class Test {
    void test(Object actionName, Object invoker, Object resultConsumer, Object errorHandler) {
        invokeApiNoLifecycleLock(actionName,
                                 // no need to send verifiable commands if the verification channel is down
                                 foo(),
                                 // comment 2
                                 // comment 3
                                 resultConsumer,
                                 errorHandler
        );

        invokeApiNoLifecycleLock(actionName,
                /* leading c-style
                 *  on multiple lines */
                                 foo(),
                /* short c-style */
                                 resultConsumer
        );

        invokeApiNoLifecycleLock(// comment first line
                // comment second line
                1,
                // intermediate
                actionName,
                /* c-style
                 */
                resultConsumer
        );

        invokeApiNoLifecycleLock(/* Weird comment
                 * fdsjafsjkd */
                1,
                // intermediate
                actionName,
                /* c-style
                 */
                resultConsumer
        );
    }

    Object foo() {
        return null;
    }

    void invokeApiNoLifecycleLock(Object... a) {
    }
}
