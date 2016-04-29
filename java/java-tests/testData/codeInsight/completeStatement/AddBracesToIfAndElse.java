
class Foo {
    {
        if (t != true<caret>)
            logger.error("SimulationClient.run error: strategy () still running. please stop it before restart.");
        else
            proxy.startStopStrategy("strategyName", true);
    }
}