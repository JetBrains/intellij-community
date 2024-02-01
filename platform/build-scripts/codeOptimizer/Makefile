VERSION := 8.5.13

install:
	mvn install:install-file -DgroupId=org.jetbrains.intellij.deps.fastutil \
      -DartifactId=intellij-deps-fastutil \
      -Dversion=$(VERSION) \
      -Dpackaging=jar \
      -Dfile=out/fastutil.jar
	mvn install:install-file -DgroupId=org.jetbrains.intellij.deps.fastutil \
      -DartifactId=intellij-deps-fastutil \
      -Dversion=$(VERSION) \
      -Dpackaging=java-source \
      -DgeneratePom=false \
      -Dfile=out/fastutil-sources.jar

deploy: install
	mvn deploy:deploy-file -DgroupId=org.jetbrains.intellij.deps.fastutil \
      -DartifactId=intellij-deps-fastutil \
      -Dversion=$(VERSION) \
      -Dpackaging=jar \
      -Dfile=out/fastutil.jar \
      -DrepositoryId=space-intellij-dependencies \
      -Durl=https://packages.jetbrains.team/maven/p/ij/intellij-dependencies
	mvn deploy:deploy-file -DgroupId=org.jetbrains.intellij.deps.fastutil \
      -DartifactId=intellij-deps-fastutil \
      -Dversion=$(VERSION) \
      -DgeneratePom=false \
      -Dpackaging=java-source \
      -Dfile=out/fastutil-sources.jar \
      -DrepositoryId=space-intellij-dependencies \
      -Durl=https://packages.jetbrains.team/maven/p/ij/intellij-dependencies