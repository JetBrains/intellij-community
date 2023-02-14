package cmd

import (
	"io/ioutil"
	"math/rand"
	"os"
	"repair/helpers"
	"repair/logger"
	"strings"
	"testing"
	"time"
)

func TestRunVmoptionAspect(t *testing.T) {
	helpers.CurrentIde.SetBinaryToWrokWith(helpers.GetRandomIdeInstallationBinary())
	testFiles := map[string]string{
		helpers.GetAbsolutePath("../helpers/test_helpers_files/test_vmoptions_files/1.vmoptions"): "More than one Garbage Collector in use",
		helpers.GetAbsolutePath("../helpers/test_helpers_files/test_vmoptions_files/2.vmoptions"): "Xmx value (current value 1048m) should be higher than Xms (current value 1228m)",
	}
	tests := []struct {
		name    string
		wantErr bool
	}{
		{
			name:    "Check test .vmoptions files",
			wantErr: true,
		},
	}
	for testFile, testError := range testFiles {
		createdFile := createTestVmoptionsFileForBinary(helpers.CurrentIde, testFile)
		rootCmd.SetArgs([]string{"vmoptions", "--path=" + helpers.GetIdeaBinaryToWrokWith()})
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				if err := vmoptionsCmd.Execute(); err != nil {
					if !strings.Contains(err.Error(), testError) {
						t.Errorf("RunVmoptionAspect() error = %v, wantErr %v", err, tt.wantErr)
					}
				}
			})
		}
		err := os.Remove(createdFile)
		logger.ExitWithExceptionOnError(err)
	}
	logger.RemoveLogFile()
}

func getRandomVmoptionFilelocation(ide helpers.IDE) (vmOptionsFile string) {
	ide.Package = helpers.GetIdeIdePackageByBinary(ide.Binary)
	rand.Seed(time.Now().UnixNano())
	i := rand.Intn(2)
	switch i {
	case 1:
		return ide.Package + ".vmoptions"
	case 0:
		return ide.GetConfigurationDirectory() + "idea.vmoptions"
	}
	return ""
}

func createTestVmoptionsFileForBinary(ide helpers.IDE, testVmoptionsFile string) string {
	testVmOptions, err := ioutil.ReadFile(testVmoptionsFile)
	file := getRandomVmoptionFilelocation(ide)
	err = ioutil.WriteFile(file, testVmOptions, 0644)
	logger.ExitWithExceptionOnError(err)
	return file
}
