# Sample library used for repository library utils tests

Deployed into tests repositories:

- `file://${project.basedir}/../sampleRepositories/repositorySnapshots` - contains version `1.0-SNAPSHOT`
- `file://${project.basedir}/../sampleRepositories/repositoryReleases` - contains version `1.0`

To modify & deploy use the appropriate run configurations. See [pom.xml](pom.xml). Don't forget to update checksums in `testProject*`
projects in test data (IDEA's xmls and imls).