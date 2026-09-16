package main

import (
	"path"
	"strings"

	"jetbrains.com/content-module-packer/internal/pluginclasspath"
)

// pluginClassPathRecord writes one plugin's record of `plugins/plugin-classpath.txt` from the classpath files of
// the component. The record format and the jar order live in the shared pluginclasspath package.
func pluginClassPathRecord(pluginDirectory string, descriptor []byte, files []sourcedFile) ([]byte, error) {
	names := make([]string, 0, len(files))
	for _, file := range files {
		if file.classPath {
			names = append(names, strings.TrimPrefix(file.RelativePath, pluginDirectory+"/"))
		}
	}
	return pluginclasspath.Record(path.Base(pluginDirectory), descriptor, names)
}
