var gulp = require('gulp')
var ts = require('gulp-typescript')
var concat = require('gulp-concat')
var uglify = require('gulp-uglify')
var newer = require('gulp-newer')
var sourcemaps = require('gulp-sourcemaps')
var path = require('path')

var outDir = 'out'
var outFile = 'ij-rpc-client.js'
var sources = "src/*.ts";

var tsProject = ts.createProject({
  target: "ES5",
  noImplicitAny: true,
  removeComments: true,
  sortOutput: true,
  module: "commonjs"
});

gulp.task("compile", function () {
  var tsResult = gulp.src(sources)
      .pipe(sourcemaps.init())
      //.pipe(newer(outDir + '/' + outFile))
      .pipe(ts(tsProject));

  tsResult.js.pipe(concat(outFile))
      //.pipe(uglify({
      //        output: {
      //          beautify: true,
      //          indent_level: 2
      //        }
      //      }))
      .pipe(sourcemaps.write('.', {includeContent: false, sourceRoot: path.resolve('testData')}))
      .pipe(gulp.dest(outDir))
});

gulp.task('watch', function () {
  gulp.watch(sources, ['compile']);
});

gulp.task('default', ['compile']);