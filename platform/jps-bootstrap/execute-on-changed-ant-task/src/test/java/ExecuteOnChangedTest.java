import org.apache.tools.ant.BuildFileRule;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.jetbrains.ExecuteOnChanged;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Paths;

public class ExecuteOnChangedTest {
    @Rule
    public final BuildFileRule buildRule = new BuildFileRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        System.setProperty("temp.dir", temporaryFolder.getRoot().getCanonicalPath());
        buildRule.configureProject("testData/build.xml");
    }

    @Test
    public void smoke() {
        buildRule.executeTarget("test.smoke");
        String log = buildRule.getLog();
        Assert.assertTrue(log, log.contains("is missing, building"));
        Assert.assertTrue(log, log.contains("UP-TO-DATE according to state file"));
        Assert.assertTrue(log, log.contains("Inputs are changed"));
    }

    @Test
    public void missingInputs() throws IOException {
        java.nio.file.Path current = Paths.get(System.getProperty("user.dir"));
        java.nio.file.Path nonExistentPath = current.resolve("some-non-existent-path");

        Project project = new Project();
        Path inputs = new Path(project);
        inputs.createPathElement().setPath(nonExistentPath.toString());
        Assert.assertEquals(nonExistentPath + ": missing\n", ExecuteOnChanged.buildManifest(inputs, false));
    }
}
