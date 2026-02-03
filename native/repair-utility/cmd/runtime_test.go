package cmd

import (
	"bytes"
	"os"
	"repair/helpers"
	"repair/logger"
	"strings"
	"testing"
)

func TestRunJbrAspect(t *testing.T) {
	helpers.CurrentIde.SetBinaryToWrokWith(helpers.GetRandomIdeInstallationBinary())
	type args struct {
		args       []string
		ideaBinary string
	}
	tests := []struct {
		name    string
		args    args
		testJdk helpers.RuntimeInfo
		wantErr string
	}{
		{
			name: "runtime8",
			args: args{
				args: []string{},
			},
			testJdk: helpers.RuntimeInfo{
				BinaryPath:          helpers.GetAbsolutePath("../helpers/test_helpers_files/jbr_packages/jbr-1.8/Contents/Home/bin/java"),
				BinaryPathDefinedAt: helpers.CurrentIde.GetDefaultConfigurationDirectory() + "idea.jdk",
				BinaryPathDefinedAs: helpers.GetAbsolutePath("../helpers/test_helpers_files/jbr_packages/jbr-1.8/"),
				Version:             "1.8",
				Architecture:        "64",
				IsBundled:           false,
			},
			wantErr: "but recomended version is 11",
		}, {
			name: "bundledRuntime",
			args: args{
				args: []string{},
			},
			testJdk: helpers.RuntimeInfo{
				IsBundled: true,
			},
			wantErr: "false",
		},
	}
	for _, tt := range tests {
		helpers.PrepareEnvironent(tt.testJdk)
		t.Run(tt.name, func(t *testing.T) {
			var buf bytes.Buffer
			logger.ErrorLogger.SetOutput(&buf)
			RunJbrAspect(tt.args.args)
			if tt.wantErr != "false" && !strings.Contains(buf.String(), tt.wantErr) {
				t.Errorf("RunJbrAspect() console ouput = %v, wantErr %v", buf.String(), tt.wantErr)
			}
		})
		helpers.ClearEnvironemnt(tt.testJdk)
		logger.RemoveLogFile()
		logger.ErrorLogger.SetOutput(os.Stdout)
	}
}
